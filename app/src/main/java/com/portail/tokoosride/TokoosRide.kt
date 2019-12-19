package com.portail.tokoosride

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationServices.getFusedLocationProviderClient
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.inputmethod.InputMethodManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.recyclerview.widget.LinearLayoutManager
import com.portail.tokoosride.adapters.SelectVehicleTypeAdapter
import com.portail.tokoosride.interfaces.RetrofitGoogleMapsRoute
import com.portail.tokoosride.models.*
import com.portail.tokoosride.utils.Routes
import com.portail.tokoosride.utils.UtilFunctions
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.portail.tokoosride.providers.LocalData
import com.portail.tokoosride.providers.UserAuth
import com.portail.tokoosride.utils.LatLngInterpolator
import com.portail.tokoosride.utils.UtilData
import com.pubnub.api.PNConfiguration
import com.pubnub.api.PubNub
import com.pubnub.api.callbacks.PNCallback
import com.pubnub.api.callbacks.SubscribeCallback
import com.pubnub.api.models.consumer.PNPublishResult
import com.pubnub.api.models.consumer.PNStatus
import com.pubnub.api.models.consumer.pubsub.PNMessageResult
import com.pubnub.api.models.consumer.pubsub.PNPresenceEventResult
import com.pusher.pushnotifications.PushNotifications
import kotlinx.android.synthetic.main.fragment_home_bottom_sheet.view.*
import kotlinx.android.synthetic.main.marker_layout.view.*
import kotlinx.android.synthetic.main.marker_time_layout.view.*
import kotlinx.android.synthetic.main.requested_rider_layout.view.*
import kotlinx.android.synthetic.main.select_addresses_layout.*
import kotlinx.android.synthetic.main.select_addresses_layout.view.*
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class TokoosRide : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var polyline: Polyline

    private val REQUEST_FINE_LOCATION = 1
    private val REQUEST_CALL = 3

    private lateinit var geocoder: Geocoder
    private var mLocationRequest: LocationRequest? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val UPDATE_INTERVAL = (10 * 60 * 1000).toLong()
    private val FASTEST_INTERVAL = (2 * 60 * 1000).toLong()
    private val MAP_ZOOM = 17.0f

    private var SELECTED_INPUT_FLAG = 1
    private val AUTOCOMPLETE_REQUEST_CODE = 2

    private var currentCity: String = ""
    private var originAddress: String = ""
    private var destinationAddress: String = ""

    private var originLatitude: Double = 0.0
    private var destinationLatitude: Double = 0.0

    private var originLongitude: Double = 0.0
    private var destinationLongitude: Double = 0.0

    private lateinit var selectedVehicleType: VehicleType

    private lateinit var progressDialog: ProgressDialog
    private var isEditing: Boolean = false

    private lateinit var localData: LocalData
    private lateinit var userAuth: UserAuth
    private lateinit var user: User

    private lateinit var pubNub: PubNub

    private var riders = mutableListOf<Rider>()
    private val requestedRiders = mutableSetOf<PubnubMessage>()

    private var INTERVAL_MILISECONDS: Long = 5000

    private var handler: Handler? = null
    private val runnableRiderRequest: Runnable = Runnable { handleResponseRequestedRiders() }

    private var driverMarker: Marker? = null
    private var userMarker: Marker? = null

    @SuppressLint("SwitchIntDef", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        localData = LocalData(this@TokoosRide)

        userAuth = UserAuth(this@TokoosRide)
        user = userAuth.getUser()!!

        currentCity = user.city.toString()

        fusedLocationClient = FusedLocationProviderClient(this@TokoosRide)
        geocoder = Geocoder(this@TokoosRide, Locale.getDefault())

        handler = Handler()

        progressDialog = ProgressDialog(this@TokoosRide)
        progressDialog.setProgressStyle(R.style.ProgressBar)
        progressDialog.setCanceledOnTouchOutside(false)

        PushNotifications.start(applicationContext, "a61202b0-9854-4756-aaef-163e6fb3151a")
        PushNotifications.addDeviceInterest(user.channel)

        val pubnubConfig = PNConfiguration()
        pubnubConfig.subscribeKey = UtilData.PUBNUB_SUBSCRIBE_KEY
        pubnubConfig.publishKey = UtilData.PUBNUB_PUBLISH_KEY
        pubnubConfig.isSecure = true

        pubNub = PubNub(pubnubConfig)
        pubNub.subscribe().channels(listOf(user.channel)).execute()

        pubNub.addListener(object : SubscribeCallback() {
            override fun status(pubnub: PubNub?, status: PNStatus?) {}
            override fun presence(pubnub: PubNub?, presence: PNPresenceEventResult?) {}
            override fun message(pubnub: PubNub?, message: PNMessageResult?) {
                runOnUiThread {
                    try {
                        val data = Gson().fromJson(message?.message!!.asString, PubnubMessage::class.java)
                        when(data.type) {
                            UtilData.TYPE_RIDER_REQUEST -> {
                                progressDialog.setMessage("Veillez Patenter")
                                progressDialog.show()
                                requestedRiders.add(data)
                                handler?.postDelayed(runnableRiderRequest, INTERVAL_MILISECONDS)
                            }
                            UtilData.TYPE_RIDER_LOCATION -> { updateDriverLocation(data.location) }
                        }

                    } catch (e: java.lang.Exception) {  }
                }
            }
        })

        Places.initialize(applicationContext, resources.getString(R.string.google_maps_key))
        val placesClient = Places.createClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val sheetBehavior = BottomSheetBehavior.from(bottom_sheet)

        sheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {

            override fun onStateChanged(view: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> { }
                    BottomSheetBehavior.STATE_EXPANDED -> { }
                    BottomSheetBehavior.STATE_COLLAPSED -> { }
                    BottomSheetBehavior.STATE_DRAGGING -> { }
                    BottomSheetBehavior.STATE_SETTLING -> { }
                }
            }

            override fun onSlide(view: View, v: Float) {}
        })

        bottom_sheet.search_input_view.setOnClickListener {

            isEditing = true

            select_addresses_view.visibility = View.VISIBLE
            select_addresses_view.destination_address.requestFocus()

            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.showSoftInput(select_addresses_view.destination_address, InputMethodManager.SHOW_IMPLICIT)

            setSupportActionBar(toolbar)
            toolbar.title = ""

            toolbar.setNavigationIcon(R.drawable.ic_back_arrow_primary_24dp)
            toolbar.setNavigationOnClickListener {  onBackPressed() }

            select_addresses_view.origin_address.setText(this.originAddress)
        }

        select_addresses_view.origin_address.setOnFocusChangeListener { _, b ->
            if(b) {
                select_addresses_view.pickup_destination_text.text = "Entrez Votre Origine"
                this.SELECTED_INPUT_FLAG = 0
                this.showPlacesIntent("Entrez Votre Origine", select_addresses_view.origin_address.text.toString())
            }
        }

        select_addresses_view.origin_address.setOnClickListener {
            select_addresses_view.pickup_destination_text.text = "Entrez Votre Origine"
            this.SELECTED_INPUT_FLAG = 0
            this.showPlacesIntent("Entrez Votre Origine", select_addresses_view.origin_address.text.toString())
        }

        select_addresses_view.destination_address.setOnFocusChangeListener { _, b ->
            if(b) {
                select_addresses_view.pickup_destination_text.text = "Entrez Votre Destination"
                this.SELECTED_INPUT_FLAG = 1
                this.showPlacesIntent("Entrez Votre Destination", select_addresses_view.destination_address.text.toString())
            }
        }

        select_addresses_view.destination_address.setOnClickListener {
            select_addresses_view.pickup_destination_text.text = "Entrez Votre Destination"
            this.SELECTED_INPUT_FLAG = 1
            this.showPlacesIntent("Entrez Votre Destination", select_addresses_view.destination_address.text.toString())
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =  getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel =  NotificationChannel(
                    user.channel,
                    user.name,
                    NotificationManager.IMPORTANCE_NONE)
            notificationChannel.lightColor = Color.TRANSPARENT
            notificationChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    //SHOW GOOGLE PLACES AUTOCOMPLETE

    private fun showPlacesIntent(hint: String, query: String) {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(
            AutocompleteActivityMode.OVERLAY, fields)
            .setCountry("UG")
            .setInitialQuery(query.split(",")[0])
            .build(this)
            .putExtra("hint", hint)

        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    //WHEN THE BACK BUTTON IS PRESSED

    override fun onBackPressed() {

        if(destinationAddress != "" && originAddress != "" && !isEditing) {

            if( bottom_sheet.select_vehicle_type.visibility == View.VISIBLE) {

                bottom_sheet.select_vehicle_type.visibility = View.GONE
                bottom_sheet.select_destination_view.visibility = View.VISIBLE
                bottom_sheet.requested_rider_layout.visibility = View.GONE
                select_addresses_view.destination_address.setText("")

                mMap.setPadding(20, 20, 20, 20)

                destinationAddress = ""
                destinationLongitude = 0.0
                destinationLatitude = 0.0
                mMap.clear()

                fusedLocationClient.lastLocation.addOnSuccessListener {
                    if(it != null){
                        try {
                            startLocationUpdates()
                            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), MAP_ZOOM))
                            this.setLocationVariable(addresses[0].getAddressLine(0), it.latitude, it.longitude, addresses[0].countryName, addresses[0].locality)
                        } catch (e: Exception){}
                    }
                }.addOnFailureListener {
                    runOnUiThread {
                        Toast.makeText(this@TokoosRide, it.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        if (select_addresses_view.visibility == View.VISIBLE) {

            isEditing = false
            select_addresses_view.visibility = View.GONE
            if(originAddress == "") { select_addresses_view.destination_address.setText("") }

            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            try { inputManager.hideSoftInputFromWindow(select_addresses_view.origin_address.windowToken, 0) } catch (e: java.lang.Exception) {}
            try { inputManager.hideSoftInputFromWindow(select_addresses_view.destination_address.windowToken, 0) } catch (e: java.lang.Exception) {}
        }
    }

    //WHEN THE MAP IS READY

    override fun onMapReady(googleMap: GoogleMap) {

        mMap = googleMap

        if(checkPermissions()) {
            mMap.isMyLocationEnabled = true
            mMap.setOnMyLocationButtonClickListener { false }
        } else { requestPermissions() }

        mMap.setOnMyLocationClickListener {
            val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
            mMap.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude)).title(addresses[0].getAddressLine(0)))
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), MAP_ZOOM))
            this.setLocationVariable(addresses[0].getAddressLine(0), it.latitude, it.longitude, addresses[0].countryName, addresses[0].locality)
        }

        if(checkPermissions()){
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if(it != null){
                    try {
                        val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), MAP_ZOOM))
                        this.setLocationVariable(addresses[0].getAddressLine(0), it.latitude, it.longitude, addresses[0].countryName, addresses[0].locality)
                    } catch (e: Exception){}
                }
            }.addOnFailureListener {
                runOnUiThread {
                    Toast.makeText(this@TokoosRide, it.message, Toast.LENGTH_LONG).show()
                }
            }
        } else { this.requestPermissions() }
    }

    //SET ORIGIN ADDRESS DATA

    private fun setLocationVariable(address: String, latitude: Double, longitude: Double, country: String, town: String){
        runOnUiThread {
            this.originAddress = address
            this.originLatitude = latitude
            this.originLongitude = longitude
            this.currentCity = town
        }
    }

    //REQUEST MAP ROUTE

    private fun getPolyline(){

        val url = "https://maps.googleapis.com/maps/"

        val origin = LatLng(originLatitude, originLongitude)
        val destination = LatLng(destinationLatitude, destinationLongitude)

        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val type = "driving"

        val service = retrofit.create(RetrofitGoogleMapsRoute::class.java)
        val call = service.getDistanceDuration("metric",  "${origin.latitude},${origin.longitude}", "${destination.latitude},${destination.longitude}", type)

        call.enqueue(object : Callback<PolylineMapRoute> {

            override fun onFailure(call: Call<PolylineMapRoute>, t: Throwable) {
                runOnUiThread {
                    Toast.makeText(this@TokoosRide, t.message, Toast.LENGTH_LONG).show()
                    progressDialog.dismiss()
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call<PolylineMapRoute>, response: Response<PolylineMapRoute>) {
                try {
                    runOnUiThread {

                        mMap.clear()

                        val markerViewOrigin = layoutInflater.inflate(R.layout.marker_layout, null, false)
                        markerViewOrigin.text_address.text = originAddress.split(",")[0]
                        markerViewOrigin.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        markerViewOrigin.layout(0, 0, markerViewOrigin.measuredWidth, markerViewOrigin.measuredHeight);

                        markerViewOrigin.isDrawingCacheEnabled = true
                        markerViewOrigin.buildDrawingCache()
                        val markerBitmapOrigin = markerViewOrigin.drawingCache

                        val bitmapOrigin = resources.getDrawable(R.drawable.ic_origin)
                        val originIcon = BitmapDescriptorFactory.fromBitmap(bitmapOrigin.toBitmap())
                        val originMarker = MarkerOptions().position(origin).title("origin").icon(originIcon)

                        mMap.addMarker(MarkerOptions().position(origin).icon(BitmapDescriptorFactory.fromBitmap(markerBitmapOrigin)))
                        mMap.addMarker(originMarker)

                        val markerViewDestination = layoutInflater.inflate(R.layout.marker_layout, null, false)
                        markerViewDestination.text_address.text = destinationAddress.split(",")[0]
                        markerViewDestination.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        markerViewDestination.layout(0, 0, markerViewDestination.measuredWidth, markerViewDestination.measuredHeight);

                        markerViewDestination.isDrawingCacheEnabled = true
                        markerViewDestination.buildDrawingCache()
                        val markerBitmapDestination = markerViewDestination.drawingCache

                        val bitmapDestination = resources.getDrawable(R.drawable.ic_destination)
                        val destinationIcon = BitmapDescriptorFactory.fromBitmap(bitmapDestination.toBitmap())
                        val destinationMarker = MarkerOptions().position(destination).title("destination").icon(destinationIcon)

                        mMap.addMarker(MarkerOptions().position(destination).icon(BitmapDescriptorFactory.fromBitmap(markerBitmapDestination)))
                        mMap.addMarker(destinationMarker)

                        mMap.setOnMarkerClickListener {

                            isEditing = true
                            select_addresses_view.visibility = View.VISIBLE

                            if(it.position == originMarker.position) {
                                select_addresses_view.pickup_destination_text.text = "Entrez Votre Origine"
                                SELECTED_INPUT_FLAG = 0
                                showPlacesIntent("Entrez Votre Origine", select_addresses_view.origin_address.text.toString())
                            }

                            if(it.position == destinationMarker.position) {
                                select_addresses_view.pickup_destination_text.text = "Entrez Votre Destination"
                                SELECTED_INPUT_FLAG = 1
                                showPlacesIntent("Entrez Votre Destination", select_addresses_view.destination_address.text.toString())
                            }

                            false
                        }

                        response.body()?.routes!!.forEach {
                            val i = response.body()?.routes!!.indexOf(it)
                            val distance = it.legs[i].distance
                            val time = it.legs[i].duration

                            val encodedString = response.body()?.routes!![0].overview_polyline.points
                            val linesList = UtilFunctions().decodePoly(encodedString)
                            polyline = mMap.addPolyline(
                                PolylineOptions()
                                    .addAll(linesList)
                                    .width(20.0f)
                                    .color(Color.parseColor("#BB00AEEF"))
                                    .geodesic(true))

                            showPrices(distance, time)
                        }

                        mMap.setMinZoomPreference(2.0f)
                        mMap.setMaxZoomPreference(14.0f)

                        val latLngBounds = LatLngBounds.builder()
                        latLngBounds.include(origin)
                        latLngBounds.include(destination)

                        val bounds = latLngBounds.build()
                        mMap.setPadding(120, 60, 120, 60)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))
                    }
                } catch (e: java.lang.Exception) {
                    runOnUiThread {
                        Toast.makeText(this@TokoosRide, e.message, Toast.LENGTH_LONG).show()
                        progressDialog.dismiss()
                    }
                }
            }
        })
    }

    //SHOW PRICES AND ROUTE ON THE MAP

    private fun setUpPrices() {
        if(select_addresses_view.origin_address.text.toString() != "" && select_addresses_view.destination_address.text.toString() != "") {
            isEditing = false
            progressDialog.setMessage("Veillez Patienter")
            progressDialog.show()
            this.getPolyline()
        }
    }

    //CALCULATE PRICES ACCRORDING TO THE ORIGIN AND DESTINATION

    private fun showPrices(distance: Distance, duration: Duration) {

        runOnUiThread {

            val url = Routes().faresRequest
            val client = OkHttpClient()

            val request = Request.Builder()
                .addHeader("Content-Type", "application/json")
                .url(url)
                .get()
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread {
                        mMap.clear()
                        progressDialog.dismiss()
                        Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val responseText = response.body()!!.string()
                    runOnUiThread {
                        try {
                            val rideTypes = Gson().fromJson<MutableList<VehicleType>>(responseText, object : TypeToken<MutableList<VehicleType>>(){}.type)
                            progressDialog.dismiss()
                            select_addresses_view.visibility = View.GONE

                            val layoutManager = LinearLayoutManager(this@TokoosRide)
                            layoutManager.orientation = LinearLayoutManager.HORIZONTAL

                            val iconsList = mutableListOf<Drawable>()
                            iconsList.add(resources.getDrawable(R.drawable.ic_boda_1))
                            iconsList.add(resources.getDrawable(R.drawable.ic_tokoss_1))
                            iconsList.add(resources.getDrawable(R.drawable.ic_tokoss_xl))
                            iconsList.add(resources.getDrawable(R.drawable.ic_tokoss_2))

                            val adapter = SelectVehicleTypeAdapter(rideTypes, distance, duration, iconsList)

                            bottom_sheet.vehicle_type_recycler_view.layoutManager = layoutManager
                            bottom_sheet.vehicle_type_recycler_view.adapter = adapter

                            bottom_sheet.select_vehicle_type.visibility = View.VISIBLE
                            bottom_sheet.select_destination_view.visibility = View.GONE

                            bottom_sheet.request_button.setOnClickListener {
                                progressDialog.setMessage("Veillez Patienter")
                                progressDialog.show()
                                selectedVehicleType = adapter.getSelected()
                                handleRequestRide(adapter.getSelected())
                            }

                        } catch (e: java.lang.Exception) {
                            mMap.clear()
                            progressDialog.dismiss()
                            Toast.makeText(this@TokoosRide, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }

    //GET RIDERS AND SEND THEM REQUEST TO GET THE CLOSER ONE

    private  fun handleRequestRide(type: VehicleType) {

        runOnUiThread {

            val url = Routes().ridersRequest
            val client = OkHttpClient()

            val jsonObject = JSONObject()
            val mediaType = MediaType.parse("application/json")

            try {
                jsonObject.put("city", currentCity)
                jsonObject.put("type", type.id.toString())
            } catch (e: JSONException) {}

            val form = RequestBody.create(mediaType, jsonObject.toString())

            val request = Request.Builder()
                .addHeader("Content-Type", "application/json")
                .url(url)
                .post(form)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    runOnUiThread {
                        mMap.clear()
                        progressDialog.dismiss()
                        Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val responseText = response.body()!!.string()
                    runOnUiThread {
                        try {
                            riders = Gson().fromJson<MutableList<Rider>>(responseText, object : TypeToken<MutableList<Rider>>(){}.type)
                            val message = PubnubMessage(
                                UtilData.TYPE_RIDER_REQUEST,
                                "J'ai besoin d'un chauffeur ${type.name}",
                                user.channel, null, null, null
                            )

                            riders.forEach {
                                pubNub.publish().message(Gson().toJson(message)).channel(it.channel).async(object : PNCallback<PNPublishResult>() {
                                    override fun onResponse(result: PNPublishResult?, status: PNStatus?) {}
                                })
                            }

                        } catch (e: java.lang.Exception) {
                            mMap.clear()
                            progressDialog.dismiss()
                            Toast.makeText(this@TokoosRide, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }

    private fun handleResponseRequestedRiders() {

        runOnUiThread {
            var myLatLng = LatLng(this.originLatitude, this.originLongitude)
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if(it != null){
                    try {
                        myLatLng = LatLng(it.latitude,it.longitude)
                    } catch (e: Exception){}
                }
            }

            requestedRiders.forEach {
                if(it.location != null) {
                    val hisLatLng = LatLng(it.location!!.latitude, it.location!!.longitude)
                    it.distance = UtilFunctions().calculateDistance(myLatLng, hisLatLng)
                }
            }

            requestedRiders.sortedBy { it.distance }
            val winner = requestedRiders.first { it.distance != null }

            val url = "https://maps.googleapis.com/maps/"

            val origin = LatLng(originLatitude, originLongitude)
            val destination = LatLng(destinationLatitude, destinationLongitude)

            val retrofit = Retrofit.Builder()
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val type = "driving"

            val service = retrofit.create(RetrofitGoogleMapsRoute::class.java)
            val call = service.getDistanceDuration("metric",  "${origin.latitude},${origin.longitude}", "${destination.latitude},${destination.longitude}", type)

            call.enqueue(object : Callback<PolylineMapRoute> {

                override fun onFailure(call: Call<PolylineMapRoute>, t: Throwable) {
                    runOnUiThread {
                        Toast.makeText(this@TokoosRide, t.message, Toast.LENGTH_LONG).show()
                        progressDialog.dismiss()
                    }
                }

                @SuppressLint("SetTextI18n")
                override fun onResponse(call: Call<PolylineMapRoute>, response: Response<PolylineMapRoute>) {

                    try {
                        runOnUiThread {
                            var time: Duration? = null
                            var distance: Distance? = null
                            response.body()?.routes!!.forEach {
                                val i = response.body()?.routes!!.indexOf(it)
                                distance = it.legs[i].distance
                                time = it.legs[i].duration
                            }

                            initiateRide(time, distance, winner, myLatLng)
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
            })
        }
    }

    //INITIATE RIDE BY SAVING AND SEND NOTIFICATION TO THE RIDER

    @SuppressLint("SetTextI18n")
    private fun initiateRide(duration: Duration?, distance: Distance?, winner: PubnubMessage, myLocation: LatLng) {

        runOnUiThread {

            try {

                val postUrl = Routes().rideInitiate
                val client = OkHttpClient()

                val jsonObject = JSONObject()
                val mediaType = MediaType.parse("application/json")

                val priceClose = UtilFunctions().calculatePriceTransport(distance!!, duration!!, selectedVehicleType, 0.95)
                val priceFar = UtilFunctions().calculatePriceTransport(distance, duration, selectedVehicleType, 1.05)
                val priceRange = "$priceClose Fc - $priceFar Fc"

                try {
                    jsonObject.put("type", 1)
                    jsonObject.put("auth", user.token)
                    jsonObject.put("rider_id", winner.data?.get("rider_id"))
                    jsonObject.put("price_range", priceRange)
                    jsonObject.put("origin_address", originAddress)
                    jsonObject.put("origin_latitude", originLatitude)
                    jsonObject.put("origin_longitude", originLongitude)
                    jsonObject.put("destination_address", destinationAddress)
                    jsonObject.put("destination_latitude", destinationLatitude)
                    jsonObject.put("destination_longitude", destinationLongitude)
                    jsonObject.put("distance", distance.value)
                    jsonObject.put("duration", distance.value)

                } catch (e: JSONException) {}

                val form = RequestBody.create(mediaType, jsonObject.toString())

                val request = Request.Builder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", user.token!!)
                    .url(postUrl)
                    .post(form)
                    .build()

                client.newCall(request).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        runOnUiThread {
                            progressDialog.dismiss()
                            Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG)
                                .show()
                        }
                    }

                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val responseText = response.body()!!.string()
                        runOnUiThread {
                            progressDialog.dismiss()
                            try {
                                if(response.code() == 200) {
                                    val ride = Gson().fromJson(responseText, Ride::class.java)
                                    localData.saveCurrentRide(Gson().toJson(ride))

                                    bottom_sheet.select_vehicle_type.visibility = View.GONE
                                    bottom_sheet.select_destination_view.visibility = View.GONE
                                    bottom_sheet.requested_rider_layout.visibility = View.VISIBLE

                                    bottom_sheet.requested_rider_layout.rider_name.text = ride.rider.fullName
                                    bottom_sheet.requested_rider_layout.rider_rating.rating = ride.rider.rate.toFloat()
                                    bottom_sheet.requested_rider_layout.rider_id.text = "${ride.rider.rideNumber}, ${ride.rider.vehicle.plateNumber}"

                                    bottom_sheet.requested_rider_layout.call_rider.setOnClickListener {
                                        if(checkCallPermission()) {
                                            try {
                                                val callIntent = Intent(Intent.ACTION_CALL);
                                                callIntent.data =
                                                    Uri.parse("tel:${ride.rider.phoneNumber}")
                                                startActivity(callIntent)
                                            } catch (e: ActivityNotFoundException) { }
                                        } else { requestCallPermission() }
                                    }

                                    bottom_sheet.requested_rider_layout.message_rider.setOnClickListener {
                                        val uri = Uri.parse("smsto:${ride.rider.phoneNumber}")
                                        val intent = Intent(Intent.ACTION_SENDTO, uri)
                                        intent.putExtra("sms_body", "Salut! Tu peux venir me prendre. Je suis à $originAddress")
                                        startActivity(intent)
                                    }

                                    val location = PubnubLocation(myLocation.latitude, myLocation.longitude)

                                    val data = mutableMapOf<String, Any>()
                                    data["ride"] = Gson().toJson(ride)

                                    val message = PubnubMessage(
                                        UtilData.TYPE_RIDER_LOCATION,
                                        "Tu peux venir me prendre",
                                        user.channel, location, null, null
                                    )
                                    pubNub.publish().message(Gson().toJson(message)).channel(ride.rider.channel).async(object : PNCallback<PNPublishResult>() {
                                        override fun onResponse(result: PNPublishResult?, status: PNStatus?) {}
                                    })

                                    mMap.clear()

                                    val origin = LatLng(originLatitude, originLongitude)
                                    val bitmapOrigin = resources.getDrawable(R.drawable.ic_origin)
                                    val originIcon = BitmapDescriptorFactory.fromBitmap(bitmapOrigin.toBitmap())
                                    val originMarker = MarkerOptions().position(origin).icon(originIcon)
                                    mMap.addMarker(originMarker)

                                    if(winner.location != null) {
                                        val latLng = LatLng(winner.location!!.longitude, winner.location!!.latitude)
                                        val bitmapCar = resources.getDrawable(R.drawable.ic_origin)
                                        val carIcon = BitmapDescriptorFactory.fromBitmap(bitmapCar.toBitmap())
                                        val carMarker = MarkerOptions().position(latLng).icon(carIcon)
                                        driverMarker = mMap.addMarker(carMarker)
                                    }

                                    requestDistanceDuration(origin)

                                } else {
                                    val serverResponse = Gson().fromJson(responseText, ServerResponse::class.java)
                                    Toast.makeText(this@TokoosRide, serverResponse.message, Toast.LENGTH_LONG).show()
                                }
                            } catch (e: java.lang.Exception) {  }
                        }
                    }
                })

            } catch (e: java.lang.Exception) {
                progressDialog.dismiss()
                Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    //UPDATE DRIVER LOCATION

    private fun updateDriverLocation(location: PubnubLocation?) {
        runOnUiThread {
            if(location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                val myLatLng = LatLng(originLatitude, originLongitude)
                if(driverMarker != null) {

                    animateCar(latLng)

                    mMap.setMinZoomPreference(2.0f)
                    mMap.setMaxZoomPreference(16.0f)

                    val latLngBounds = LatLngBounds.builder()
                    latLngBounds.include(latLng)
                    latLngBounds.include(myLatLng)

                    val bounds = latLngBounds.build()
                    mMap.setPadding(120, 60, 120, 60)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0))

                    val contains = mMap.projection
                        .visibleRegion
                        .latLngBounds
                        .contains(latLng)

                    if (!contains) { mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng)) }

                } else {
                    val bitmapCar = resources.getDrawable(R.drawable.ic_origin)
                    val carIcon = BitmapDescriptorFactory.fromBitmap(bitmapCar.toBitmap())
                    val carMarker = MarkerOptions().position(latLng).icon(carIcon)
                    driverMarker = mMap.addMarker(carMarker)
                }
            }
        }
    }

    public fun requestDistanceDuration(driverLocation: LatLng) {

        val url = "https://maps.googleapis.com/maps/"

        val userLocation = LatLng(originLatitude, originLongitude)

        val retrofit = Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val type = "driving"

        val service = retrofit.create(RetrofitGoogleMapsRoute::class.java)
        val call = service.getDistanceDuration("metric",  "${driverLocation.latitude},${driverLocation.longitude}", "${userLocation.latitude},${userLocation.longitude}", type)

        call.enqueue(object : Callback<PolylineMapRoute> {

            override fun onFailure(call: Call<PolylineMapRoute>, t: Throwable) {
                runOnUiThread {
                    Toast.makeText(this@TokoosRide, t.message, Toast.LENGTH_LONG).show()
                    progressDialog.dismiss()
                }
            }

            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call<PolylineMapRoute>, response: Response<PolylineMapRoute>) {

                try {
                    runOnUiThread {
                        var time: Duration? = null
                        var distance: Distance? = null
                        response.body()?.routes!!.forEach {
                            val i = response.body()?.routes!!.indexOf(it)
                            distance = it.legs[i].distance
                            time = it.legs[i].duration
                        }

                        userMarker?.remove()

                        val markerViewOrigin = layoutInflater.inflate(R.layout.marker_time_layout, null, false)
                        markerViewOrigin.text_address_else.text = originAddress.split(",")[0]
                        markerViewOrigin.reach_time_else.text = "${time!!.value / 60}"
                        markerViewOrigin.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        markerViewOrigin.layout(0, 0, markerViewOrigin.measuredWidth, markerViewOrigin.measuredHeight);

                        markerViewOrigin.isDrawingCacheEnabled = true
                        markerViewOrigin.buildDrawingCache()
                        val markerBitmapOrigin = markerViewOrigin.drawingCache

                        userMarker = mMap.addMarker(MarkerOptions().position(userLocation).icon(BitmapDescriptorFactory.fromBitmap(markerBitmapOrigin)))

                        val notificationId = Math.random().toInt()

                        if((time!!.value / 60) <= 0.3 || (distance!!.value / 1000) <= 0.1) {
                            val builder = NotificationCompat.Builder(this@TokoosRide, user.channel)
                                .setSmallIcon(R.drawable.ic_car_icon_map)
                                .setContentTitle("Prêt!")
                                .setContentText("Votre chauffeur est arrivé")
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            with(NotificationManagerCompat.from(this@TokoosRide)) {
                                notify(notificationId, builder.build())
                            }
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@TokoosRide, e.localizedMessage, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        })
    }

    //ANIMATE CAR

    private fun animateCar(destination: LatLng ) {
        if(driverMarker != null) {
            val startPosition  = driverMarker?.position
            val endPosition = LatLng(destination.latitude, destination.longitude)
            val latLngInterpolator = LatLngInterpolator.LinearFixed()

            val valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f)
            valueAnimator.duration = 5000
            valueAnimator.interpolator = LinearInterpolator() as TimeInterpolator?
            valueAnimator.addUpdateListener { animation ->
                try {
                    val value = animation.animatedFraction
                    val newPosition = latLngInterpolator.interpolate(value, startPosition!!, endPosition)
                    driverMarker!!.position = newPosition
                } catch (e: java.lang.Exception) { }
            }
            valueAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                }
            })
            valueAnimator.start()
        }
    }

    //REQUEST LOCATION EVERY X TIME

    private fun startLocationUpdates() {

        mLocationRequest = LocationRequest()
        mLocationRequest!!.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest!!.interval = UPDATE_INTERVAL
        mLocationRequest!!.fastestInterval = FASTEST_INTERVAL

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(mLocationRequest!!)
        val locationSettingsRequest = builder.build()

        val settingsClient = LocationServices.getSettingsClient(this)
        settingsClient.checkLocationSettings(locationSettingsRequest)

        getFusedLocationProviderClient(this@TokoosRide).requestLocationUpdates(
            mLocationRequest, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    onLocationChanged(locationResult!!.lastLocation)
                }
            },
            Looper.myLooper()
        )
    }

    private fun onLocationChanged(location: Location?) {
        if (location != null) {
            try {
                runOnUiThread {
                    val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    setLocationVariable(address[0].getAddressLine(0), location.latitude, location.longitude, address[0].countryName, address[0].locality)
                }
            } catch (e: java.lang.Exception){  }
        }
    }

    //LOCATION PERMISSION

    private fun checkPermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) { true } else { requestPermissions(); false }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            REQUEST_FINE_LOCATION
        )
    }

    private fun checkCallPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
        ) { true } else { requestCallPermission(); false }
    }

    private fun requestCallPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CALL_PHONE),
            REQUEST_CALL
        )
    }

    //LOCATION PERMISSION RESULT

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){
            REQUEST_FINE_LOCATION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startActivity(Intent(this@TokoosRide, TokoosRide::class.java))
                } else { requestPermissions() }
            }
        }
    }

    //GOOGLE PLACES AUTOCOMPLETE

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode) {
            AUTOCOMPLETE_REQUEST_CODE -> {
                when (resultCode) {
                    RESULT_OK -> {
                        val place = Autocomplete.getPlaceFromIntent(data!!)
                        if(SELECTED_INPUT_FLAG == 0) {
                            this.originAddress = "${place.name}, ${place.address}"
                            this.originLatitude = place.latLng?.latitude!!
                            this.originLongitude = place.latLng?.longitude!!
                            select_addresses_view.origin_address.setText(this.originAddress)
                        } else if (SELECTED_INPUT_FLAG == 1) {
                            this.destinationAddress = "${place.name}, ${place.address}"
                            this.destinationLatitude = place.latLng?.latitude!!
                            this.destinationLongitude = place.latLng?.longitude!!
                            select_addresses_view.destination_address.setText(this.destinationAddress)
                        }
                        this.setUpPrices()
                    }
                    AutocompleteActivity.RESULT_ERROR -> {
                        val status = Autocomplete.getStatusFromIntent(data!!)
                    }
                    RESULT_CANCELED -> { }
                }
            }
        }
    }
}
