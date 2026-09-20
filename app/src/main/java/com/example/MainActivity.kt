package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.personas.PersonaListScreen
import com.example.ui.server.LocalApiServerScreen
import com.example.ui.servers.ServerListScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val chatViewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkTheme by chatViewModel.isDarkTheme.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "chat"
                    ) {
                        composable("chat") {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onNavigateToServers = { navController.navigate("servers") },
                                onNavigateToLocalServer = { navController.navigate("local_server") },
                                onNavigateToPersonas = { navController.navigate("personas") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToOfflineModels = { navController.navigate("offline_models") }
                            )
                        }

                        composable("offline_models") {
                            com.example.ui.offline.OfflineModelsScreen(
                                viewModel = chatViewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onStartChatWithModel = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("local_server") {
                            LocalApiServerScreen(
                                viewModel = chatViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("servers") {
                            val servers by chatViewModel.servers.collectAsState()
                            ServerListScreen(
                                viewModel = chatViewModel,
                                servers = servers,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("personas") {
                            val personas by chatViewModel.personas.collectAsState()
                            val activePersona by chatViewModel.activePersona.collectAsState()
                            PersonaListScreen(
                                viewModel = chatViewModel,
                                personas = personas,
                                activePersona = activePersona,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                viewModel = chatViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
