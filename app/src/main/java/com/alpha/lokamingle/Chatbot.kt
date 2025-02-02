package com.alpha.lokamingle

import ChatAdapter
import Message
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient


class Chatbot : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private lateinit var inputField: AutoCompleteTextView
    private val messages = mutableListOf<Message>()
    private val commands = listOf(
        "Hello",
        "How are you?",
        "Show community around me",
        "Bye"
    )

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val placesApiKey = "" // Replace with your actual API key

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chatbot)

        // Initialize Places API
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, placesApiKey)
        }
        placesClient = Places.createClient(this)

        // Initialize FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(messages)
        recyclerView.adapter = adapter

        // Add welcome message from the bot
        messages.add(Message("Hi! I'm Lokamingle. How can I help you? :)", isUser = false))
        adapter.notifyItemInserted(messages.size - 1)

        // Set up input field with suggestions
        inputField = findViewById(R.id.inputField)
        val autoCompleteAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, commands)
        inputField.setAdapter(autoCompleteAdapter)

        // Handle send button
        val sendButton: ImageButton = findViewById(R.id.sendButton)
        sendButton.setOnClickListener {
            val userMessage = inputField.text.toString()
            if (userMessage.isNotBlank()) {
                handleUserInput(userMessage)
                inputField.text.clear()
            } else {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            }
        }

        // Request location permissions
        if (!checkLocationPermissions()) {
            requestLocationPermissions()
        }
    }

    private fun handleUserInput(userMessage: String) {
        messages.add(Message(userMessage, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)

        if (userMessage.equals("Show community around me", ignoreCase = true)) {
            getUserLocation()
        } else {
            val botResponse = "I'm here to help with your queries!"
            messages.add(Message(botResponse, isUser = false))
            adapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch the user's last known location
        fusedLocationClient.lastLocation.addOnCompleteListener { task: Task<android.location.Location> ->
            if (task.isSuccessful) {
                val location = task.result
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude
                    fetchNearbyCommunities(latitude, longitude)
                } else {
                    Toast.makeText(this, "Failed to retrieve location", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Location fetch failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchNearbyCommunities(latitude: Double, longitude: Double) {
        val placeFields = listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES)
        val request = FindCurrentPlaceRequest.newInstance(placeFields)

        // Fetch places near the user's location
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            placesClient.findCurrentPlace(request)
                .addOnSuccessListener { response ->
                    val eventsList = StringBuilder()
                    for (placeLikelihood in response.placeLikelihoods) {
                        val place = placeLikelihood.place
                        val name = place.name
                        val address = place.address
                        val types = place.types

                        // Check if the place is relevant (e.g., parks, community centers, etc.)
                        if (types != null && (types.contains(Place.Type.PARK) || types.contains(Place.Type.MUSEUM))) {
                            eventsList.append("$name - $address\n")
                        }
                    }

                    if (eventsList.isNotEmpty()) {
                        messages.add(Message("Nearby Communities or Events:\n$eventsList", isUser = false))
                    } else {
                        messages.add(Message("No nearby communities or events found.", isUser = false))
                    }

                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerView.scrollToPosition(messages.size - 1)
                }
                .addOnFailureListener { exception ->
                    // Handle failure in a more meaningful way
                    val errorMessage = exception.message ?: "Unknown error"
                    Toast.makeText(this, "Error fetching communities: $errorMessage", Toast.LENGTH_LONG).show()
                }
        } else {
            // If permission isn't granted, ask the user to grant it
            requestLocationPermissions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserLocation()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}