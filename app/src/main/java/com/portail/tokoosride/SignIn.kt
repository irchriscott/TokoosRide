package com.portail.tokoosride

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.animation.AnimationUtils
import kotlinx.android.synthetic.main.activity_sign_in.*
import android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.portail.tokoosride.models.ServerResponse
import com.portail.tokoosride.models.User
import com.portail.tokoosride.providers.UserAuth
import com.portail.tokoosride.utils.Routes
import com.google.gson.Gson
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception

class SignIn : AppCompatActivity() {

    private var handler: Handler? = null
    private val runnable: Runnable = Runnable {
        bike.visibility = View.GONE
        car.visibility = View.VISIBLE
        val rightToLeftAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.car_right_to_left)
        car.startAnimation(rightToLeftAnimation)
    }

    @SuppressLint("DefaultLocale", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)

        val spanText = SpannableString("TokoosRide".toUpperCase())
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorPrimary)), 0, 6, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorAccent)), 6, spanText.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)

        val leftToRightAnimation = AnimationUtils.loadAnimation(applicationContext, R.anim.bike_left_to_right)
        bike.startAnimation(leftToRightAnimation)
        car.startAnimation(leftToRightAnimation)

        handler = Handler()
        handler?.postDelayed(runnable, 5000)

        country_code.registerCarrierNumberEditText(phone_number)

        phone_number_label.setOnClickListener {
            app_name_slogan.visibility = View.GONE
            login_form_placeholder.visibility = View.GONE
            login_form_elements.visibility = View.VISIBLE

            sign_up_view.background = resources.getDrawable(R.color.colorWhite)
            phone_number.requestFocus()

            val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputManager.showSoftInput(phone_number, SHOW_IMPLICIT)

            setSupportActionBar(toolbar)
            toolbar.title = ""

            toolbar.setNavigationIcon(R.drawable.ic_back_arrow_primary_24dp)
            toolbar.setNavigationOnClickListener { onBackPressed() }
        }

        submit_button.setOnClickListener {
            if(country_code.isValidFullNumber) {
                error_text.visibility = View.GONE
                error_text.text = ""
                submit_button.isClickable = false
                submit_button.alpha = 0.4f
                this.submitRegisterPhoneNumber(country_code.fullNumberWithPlus)
            } else {
                error_text.visibility = View.VISIBLE
                error_text.text = "Entrez un numéro de téléphone correct"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(runnable)
    }

    override fun onBackPressed() {
        app_name_slogan.visibility = View.VISIBLE
        login_form_placeholder.visibility = View.VISIBLE
        login_form_elements.visibility = View.GONE

        sign_up_view.background = resources.getDrawable(R.color.colorPrimary)

        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(phone_number.windowToken, 0)
    }

    @SuppressLint("SetTextI18n")
    private fun submitRegisterPhoneNumber(phone: String) {

        val url = Routes().registerPhoneNumber

        val client = OkHttpClient()
        val jsonObject = JSONObject()
        val mediaType = MediaType.parse("application/json")

        try { jsonObject.put("phone", phone) }
        catch (e: JSONException) {}

        val form = RequestBody.create(mediaType, jsonObject.toString())
        val request = Request.Builder()
            .addHeader("Content-Type", "application/json")
            .url(url)
            .post(form)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    submit_button.isClickable = true
                    submit_button.alpha = 1.0f
                    Toast.makeText(this@SignIn, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body()!!.string()
                runOnUiThread {
                    submit_button.isClickable = true
                    submit_button.alpha = 1.0f
                    if(response.code() == 200) {
                        try {
                            val user = Gson().fromJson(responseText, User::class.java)
                            if(user.email != null && user.name != null) {
                                val userAuth = UserAuth(this@SignIn)
                                userAuth.saveAuthStage(4)
                                userAuth.saveUser(Gson().toJson(user))
                                startActivity(Intent(this@SignIn, TokoosRide::class.java))
                            } else {
                                val userAuth = UserAuth(this@SignIn)
                                userAuth.saveAuthStage(1)
                                userAuth.saveUser(Gson().toJson(user))
                                startActivity(Intent(this@SignIn, AuthenticateUser::class.java))
                            }
                        } catch (e: Exception){}
                    } else {
                        try {
                            val serverResponse = Gson().fromJson(responseText, ServerResponse::class.java)
                            error_text.visibility = View.VISIBLE
                            error_text.text = "${serverResponse.message} : ${serverResponse.args.joinToString(", ")}"
                        } catch (e: Exception){}
                    }
                }
            }
        })
    }
}
