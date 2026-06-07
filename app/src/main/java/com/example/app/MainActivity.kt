package com.example.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.rememberNavController
import com.example.app.navigation.AppNavigation
import com.example.app.ui.components.pairing.GlobalPairingModals
import com.example.app.ui.theme.KushoTheme
import com.example.airphabets.common.MessageService
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity() {

    private lateinit var messageService: MessageService
    private lateinit var nodeClient: NodeClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        messageService = MessageService(this)
        nodeClient = Wearable.getNodeClient(this)

        setContent {
            KushoTheme {
                val navController = rememberNavController()

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.White
                    ) { innerPadding ->
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }

                    GlobalPairingModals()
                }
            }
        }
    }
}
