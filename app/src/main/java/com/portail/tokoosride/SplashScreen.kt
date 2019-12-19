package com.portail.tokoosride

import android.annotation.SuppressLint
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import com.portail.tokoosride.providers.UserAuth
import kotlinx.android.synthetic.main.activity_splash_screen.*

class SplashScreen : AppCompatActivity() {

    private var handler: Handler? = null
    private val runnable: Runnable = Runnable {
        if(userAuth.isLoggedIn()) {
            when (userAuth.getAuthStage()) {
                0 -> {
                    startActivity(Intent(this@SplashScreen, SignIn::class.java))
                }
                1 -> {
                    startActivity(Intent(this@SplashScreen, AuthenticateUser::class.java))
                }
                2 -> {
                    startActivity(Intent(this@SplashScreen, IdentityRegistration::class.java))
                }
                else -> {
                    startActivity(Intent(this@SplashScreen, TokoosRide::class.java))
                }
            }
        } else {
            startActivity(Intent(this@SplashScreen, SignIn::class.java))
        }
    }

    private lateinit var userAuth: UserAuth

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        userAuth = UserAuth(this@SplashScreen)

        val spanText = SpannableString("TokoosRide".toUpperCase())
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorPrimary)), 0, 6, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        spanText.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorAccent)), 6, spanText.length, Spannable.SPAN_EXCLUSIVE_INCLUSIVE)
        app_name.text = spanText

        handler = Handler()
        handler?.postDelayed(runnable, 3000)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(runnable)
    }
}
