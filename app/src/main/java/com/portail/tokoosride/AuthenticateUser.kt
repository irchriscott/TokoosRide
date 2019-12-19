package com.portail.tokoosride

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.portail.tokoosride.models.ServerResponse
import com.portail.tokoosride.models.User
import com.portail.tokoosride.providers.UserAuth
import com.portail.tokoosride.utils.Routes
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_authenticate_user.*
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.lang.Exception

class AuthenticateUser : AppCompatActivity() {

    lateinit var userAuth: UserAuth
    lateinit var user: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authenticate_user)

        userAuth = UserAuth(this@AuthenticateUser)
        user = userAuth.getUser()!!

        code_input.showKeyBoard(this@AuthenticateUser)
        code_input.requestFocus()

        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.showSoftInput(code_input, InputMethodManager.SHOW_IMPLICIT)

        submit_button.setOnClickListener {
            error_text.visibility = View.GONE
            error_text.text = ""
            submit_button.isClickable = false
            submit_button.alpha = 0.4f
            this.authenticatePhoneNumber(code_input.text.toString())
        }
    }

    @SuppressLint("SetTextI18n")
    private fun authenticatePhoneNumber(code: String) {

        //API URL
        val url = Routes().authPhoneNumber

        //API REQUEST CLIENT
        val client = OkHttpClient()

        val jsonObject = JSONObject()
        val mediaType = MediaType.parse("application/json")

        try {
            jsonObject.put("code", code)
            jsonObject.put("auth", user.token)
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
                    Toast.makeText(this@AuthenticateUser, e.localizedMessage, Toast.LENGTH_LONG).show()
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
                                startActivity(Intent(this@AuthenticateUser, TokoosRide::class.java))
                            } else {
                                val userAuth = UserAuth(this@AuthenticateUser)
                                userAuth.saveAuthStage(2)
                                userAuth.saveUser(Gson().toJson(user))
                                startActivity(Intent(this@AuthenticateUser, IdentityRegistration::class.java))
                            }
                        } catch (e: Exception){}
                    } else {
                        try {
                            val serverResponse = Gson().fromJson(responseText, ServerResponse::class.java)
                            error_text.visibility = View.VISIBLE
                            error_text.text = serverResponse.message
                        } catch (e: Exception){}
                    }
                }
            }
        })
    }

    override fun onBackPressed() {}
}
