package com.home.expensetracker.ui.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import androidx.appcompat.app.AppCompatActivity
import com.home.expensetracker.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fade in the "Powered by Anil" banner smoothly
        binding.tvPoweredBySplash.alpha = 0f
        binding.tvPoweredBySplash.visibility = View.VISIBLE

        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 900
            startOffset = 400         // slight delay after screen appears
            fillAfter = true
        }
        binding.tvPoweredBySplash.startAnimation(fadeIn)

        // Navigate to MainActivity after 2.5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 2500)
    }
}
