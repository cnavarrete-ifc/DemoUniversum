package com.example.demouniversum

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Variables que apuntan a cada botón
        val button1 = findViewById<CardView>(R.id.app1button)
        val button2 = findViewById<CardView>(R.id.app2button)
        val button3 = findViewById<CardView>(R.id.app3button)
        val button4 = findViewById<CardView>(R.id.app4button)

        // Listener para el primer botón
        button1.setOnClickListener {
            val intent = Intent(this, FreqAmp::class.java)
            startActivity(intent)
        }

        // Listener para el segundo botón
        button2.setOnClickListener {
            val intent = Intent(this, Binaural::class.java)
            startActivity(intent)
        }

        // Listener para el tercer botón
        button3.setOnClickListener {
            val intent = Intent(this, Espectrograma::class.java)
            startActivity(intent)
        }

        // Listener para el cuarto botón
        button4.setOnClickListener {
            val intent = Intent(this, Binaural2::class.java)
            startActivity(intent)
        }
    }
}