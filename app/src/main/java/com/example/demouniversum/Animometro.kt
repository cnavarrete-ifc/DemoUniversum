package com.example.demouniversum

import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.io.IOException

class Animometro : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_animometro)

        val gridLayout = findViewById<GridLayout>(R.id.gridLayout)
        for (i in 0 until gridLayout.childCount) {
            val button = gridLayout.getChildAt(i) as Button
            button.setOnClickListener { onEmotionSelected(it) }
        }
    }

    private fun onEmotionSelected(view: View) {
        val button = view as Button
        val emotion = button.text.toString()

        saveResponseToTXT(emotion)
        finish() // Regresar a la activity anterior
    }

    private fun saveResponseToTXT(response: String) {
        val stimulus = "default"
        val file = File(getExternalFilesDir(null), "animometro_respuestas.txt")
        val fileExists = file.exists()

        try {
            val writer = FileWriter(file, true) // 'true' para añadir al final del archivo

            var lastId = 0
            if (fileExists && file.length() > 0) {
                file.useLines { lines ->
                    val lastLine = lines.lastOrNull()
                    if (lastLine != null && lastLine.isNotBlank() && !lastLine.startsWith("ID")) {
                        try {
                            lastId = lastLine.split(",")[0].toInt()
                        } catch (e: NumberFormatException) {
                            // El encabezado o una línea corrupta pueden causar esto
                            Log.e("Animometro", "Error al parsear el ID de la última línea", e)
                        }
                    }
                }
            } else {
                writer.append("ID,Estímulo,Respuesta\n")
            }

            val newId = lastId + 1
            val csvData = "$newId,$stimulus,$response\n"

            writer.append(csvData)
            writer.flush()
            writer.close()
            Toast.makeText(this, "Respuesta guardada", Toast.LENGTH_SHORT).show()
        } catch (e: IOException) {
            Log.e("Animometro", "Error al guardar el archivo TXT", e)
            Toast.makeText(this, "Error al guardar la respuesta", Toast.LENGTH_SHORT).show()
        }
    }
}
