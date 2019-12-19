package com.portail.tokoosride


import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.portail.tokoosride.models.ServerResponse
import com.portail.tokoosride.models.User
import com.portail.tokoosride.providers.UserAuth
import com.portail.tokoosride.utils.Routes
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_identity_registration.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception

class IdentityRegistration : AppCompatActivity() {

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val REQUEST_FINE_LOCATION = 1

    lateinit var userAuth: UserAuth
    lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_identity_registration)

        userAuth = UserAuth(this@IdentityRegistration)
        user = userAuth.getUser()!!

        fusedLocationProviderClient = FusedLocationProviderClient(this@IdentityRegistration)
        this.getUserLocation()

        submit_button.setOnClickListener {
            submit_button.isClickable = true
            submit_button.alpha = 0.4f
            this.submitUserIdentity()
        }
    }

    @SuppressLint("SetTextI18n")
    private  fun submitUserIdentity() {
        //API URL
        val url = Routes().registerIdentity

        //API REQUEST CLIENT
        val client = OkHttpClient()

        val jsonObject = JSONObject()
        val mediaType = MediaType.parse("application/json")

        try {
            jsonObject.put("auth", user.token)
            jsonObject.put("name", name_input.text.toString())
            jsonObject.put("email", email_input.text.toString())
            jsonObject.put("country", country_input.text.toString())
            jsonObject.put("city", city_input.text.toString())
        }
        catch (e: JSONException) {}

        //FORM TO SEND TO THE API
        val form = RequestBody.create(mediaType, jsonObject.toString())

        //API REQUEST
        val request = Request.Builder()
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", user.token!!)
            .url(url)
            .post(form)
            .build()

        //SEND REQUEST
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    submit_button.isClickable = true
                    submit_button.alpha = 1.0f
                    Toast.makeText(this@IdentityRegistration, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body()!!.string()
                runOnUiThread {
                    submit_button.isClickable = true
                    submit_button.alpha = 1.0f
                    if(response.code() == 200) {
                        try {
                            val sessionUser = Gson().fromJson(responseText, User::class.java)
                            userAuth.saveAuthStage(3)
                            userAuth.saveUser(Gson().toJson(sessionUser))
                            startActivity(Intent(this@IdentityRegistration, TokoosRide::class.java))
                        } catch (e: Exception){
                            Log.i("ERROR", e.message!!)
                        }
                    } else {
                        try {
                            val serverResponse = Gson().fromJson(responseText, ServerResponse::class.java)
                            error_text.visibility = View.VISIBLE
                            error_text.text = "${serverResponse.message} : ${serverResponse.args.joinToString(", ")}"
                        } catch (e: Exception){
                            Log.i("ERROR", e.message!!)
                        }
                    }
                }
            }
        })
    }

    private fun getUserLocation() {
        if (ContextCompat.checkSelfPermission(this@IdentityRegistration, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationProviderClient.lastLocation.addOnSuccessListener {
                    if(it != null) {
                        val geocoder = Geocoder(this@IdentityRegistration)
                        try {
                            val result = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            runOnUiThread {
                                country_input.setText(result[0].countryName)
                                city_input.setText(result[0].locality)
                            }
                        } catch (e: Exception){
                            runOnUiThread {
                                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }.addOnFailureListener {
                    runOnUiThread { Toast.makeText(this@IdentityRegistration, it.localizedMessage, Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        } else {
            ActivityCompat.requestPermissions(this@IdentityRegistration, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_FINE_LOCATION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when(requestCode) {
            REQUEST_FINE_LOCATION -> {
                if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    this.getUserLocation()
                }
            }
        }
    }
}
