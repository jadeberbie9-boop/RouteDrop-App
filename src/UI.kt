package com.example.myapplication

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Typography
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.routedrop.AppViewModel
import com.example.myapplication.routedrop.AuthManager
import com.example.myapplication.routedrop.Customer
import com.example.myapplication.routedrop.Delivery
import com.example.myapplication.routedrop.LoginScreen
import com.example.myapplication.routedrop.OsmSetup
import com.example.myapplication.routedrop.RouteDropScreen
import com.example.myapplication.routedrop.SignUpScreen
import com.example.myapplication.routedrop.User
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ───── UiState.kt ─────
sealed interface UiState {

    data object Initial : UiState
    data object Loading : UiState
    data class Success(val outputText: String) : UiState
    data class Error(val errorMessage: String) : UiState
}
// ───── Color.kt ─────
// Primary Brand Colors (Vibrant Delivery Theme)
val DeliveryBlue = Color(0xFF007AFF) // Electric Blue for high contrast, trustworthy brand feel
val DeliveryBlueLight = Color(0xFFE5F1FF) // Very light blue for backgrounds/containers
val DeliveryBlueDark = Color(0xFF0056B3)

// Secondary Colors
val AccentOrange = Color(0xFFFF9500) // Energetic Orange for active delivery tags/statuses
val AccentGreen = Color(0xFF34C759) // Success Green for Delivered states

// Neutral/Backgrounds (Clean and crisp)
val BgLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF2F2F7)

// Dark Theme Neutrals
val BgDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantDark = Color(0xFF2C2C2E)

// Text Colors
val TextPrimaryLight = Color(0xFF1C1C1E)
val TextSecondaryLight = Color(0xFF636366)
val TextPrimaryDark = Color(0xFFE5E5EA)
val TextSecondaryDark = Color(0xFFAEAEB2)
// ───── Type.kt ─────
// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
// ───── Theme.kt ─────
private val DarkColorScheme = darkColorScheme(
    primary = DeliveryBlue,
    onPrimary = Color.White,
    primaryContainer = DeliveryBlueDark,
    onPrimaryContainer = Color.White,

    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantDark,
    onSecondaryContainer = TextPrimaryDark,

    background = BgDark,
    onBackground = TextPrimaryDark,

    surface = SurfaceDark,
    onSurface = TextPrimaryDark,

    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,

    error = Color(0xFFFF453A)
)

private val LightColorScheme = lightColorScheme(
    primary = DeliveryBlue,
    onPrimary = Color.White,
    primaryContainer = DeliveryBlueLight,
    onPrimaryContainer = DeliveryBlueDark,

    secondary = AccentOrange,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = TextPrimaryLight,

    background = BgLight,
    onBackground = TextPrimaryLight,

    surface = SurfaceLight,
    onSurface = TextPrimaryLight,

    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,

    error = Color(0xFFFF3B30)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamic color to false so our Sand & Black theme is always applied
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
// ───── BakingViewModel.kt ─────
class BakingViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> =
        _uiState.asStateFlow()

    private val generativeModel = Firebase.ai.generativeModel(
        modelName = "gemini-flash-latest",
    )

    fun sendPrompt(bitmap: Bitmap, prompt: String) {
        _uiState.value = UiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = generativeModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
                response.text?.let { outputContent ->
                    _uiState.value = UiState.Success(outputContent)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "")
            }
        }
    }
}
// ───── BakingScreen.kt ─────
val images = arrayOf(
    // Image generated using Gemini from the prompt "cupcake image"
    R.drawable.baked_goods_1,
    // Image generated using Gemini from the prompt "cookies images"
    R.drawable.baked_goods_2,
    // Image generated using Gemini from the prompt "cake images"
    R.drawable.baked_goods_3,
)
val imageDescriptions = arrayOf(
    R.string.image1_description,
    R.string.image2_description,
    R.string.image3_description,
)

@Composable
fun BakingScreen(
    bakingViewModel: BakingViewModel = viewModel()
) {
    val selectedImage = remember { mutableIntStateOf(0) }
    val placeholderPrompt = stringResource(R.string.prompt_placeholder)
    val placeholderResult = stringResource(R.string.results_placeholder)
    var prompt by rememberSaveable { mutableStateOf(placeholderPrompt) }
    var result by rememberSaveable { mutableStateOf(placeholderResult) }
    val uiState by bakingViewModel.uiState.collectAsState()
    val resources = LocalResources.current

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) {
            Text(
                text = stringResource(R.string.baking_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(images) { index, image ->
                    var imageModifier = Modifier
                        .padding(start = 8.dp, end = 8.dp)
                        .requiredSize(200.dp)
                        .clickable {
                            selectedImage.intValue = index
                        }
                    if (index == selectedImage.intValue) {
                        imageModifier =
                            imageModifier.border(
                                BorderStroke(
                                    4.dp,
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                    }
                    Image(
                        painter = painterResource(image),
                        contentDescription = stringResource(imageDescriptions[index]),
                        modifier = imageModifier
                    )
                }
            }

            Row(
                modifier = Modifier.padding(all = 16.dp)
            ) {
                TextField(
                    value = prompt,
                    label = { Text(stringResource(R.string.label_prompt)) },
                    onValueChange = { prompt = it },
                    modifier = Modifier
                        .weight(0.8f)
                        .padding(end = 16.dp)
                        .align(Alignment.CenterVertically)
                )

                Button(
                    onClick = {
                        val bitmap = BitmapFactory.decodeResource(
                            resources,
                            images[selectedImage.intValue]
                        )
                        bakingViewModel.sendPrompt(bitmap, prompt)
                    },
                    enabled = prompt.isNotEmpty(),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                ) {
                    Text(text = stringResource(R.string.action_go))
                }
            }

            if (uiState is UiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                var textColor = MaterialTheme.colorScheme.onSurface
                if (uiState is UiState.Error) {
                    textColor = MaterialTheme.colorScheme.error
                    result = (uiState as UiState.Error).errorMessage
                } else if (uiState is UiState.Success) {
                    textColor = MaterialTheme.colorScheme.onSurface
                    result = (uiState as UiState.Success).outputText
                }
                val scrollState = rememberScrollState()
                Text(
                    text = result,
                    textAlign = TextAlign.Start,
                    color = textColor,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(16.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
fun BakingScreenPreview() {
    BakingScreen()
}
// ───── MainActivity.kt ─────
enum class Screen {
    LOGIN, SIGNUP, HOME
}

class MyApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        OsmSetup.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val context = LocalContext.current
                    val authManager = remember { AuthManager(context) }

                    val appViewModel: AppViewModel = viewModel()
                    val registeredUsers = appViewModel.registeredUsers
                    val globalDeliveries = appViewModel.globalDeliveries

                    var currentScreen by remember { mutableStateOf(Screen.LOGIN) }
                    var loggedInUser by remember { mutableStateOf<User?>(null) }

                    // Auto-login on launch
                    LaunchedEffect(Unit) {
                        authManager.getSavedUser()?.let { savedUser ->
                            registeredUsers.add(savedUser)
                            loggedInUser = savedUser
                            currentScreen = Screen.HOME
                        }
                    }

                    when (currentScreen) {
                        Screen.LOGIN -> {
                            LoginScreen(
                                registeredUsers = registeredUsers,
                                onLoginSuccess = { user ->
                                    authManager.saveUser(user)
                                    loggedInUser = user
                                    currentScreen = Screen.HOME
                                },
                                onNavigateToSignUp = {
                                    currentScreen = Screen.SIGNUP
                                },
                                onGuestLogin = {
                                    val guestUser = Customer(
                                        userId = "guest_${System.currentTimeMillis()}",
                                        name = "Guest User",
                                        password = "",
                                        emailAddress = "guest@example.com",
                                        phoneNumber = "Not Provided",
                                        address = "Exploring Mode"
                                    )
                                    // Don't save guest in SharedPreferences so it doesn't auto-login next time
                                    loggedInUser = guestUser
                                    currentScreen = Screen.HOME
                                }
                            )
                        }
                        Screen.SIGNUP -> {
                            SignUpScreen(
                                onSignUpSuccess = { newUser ->
                                    authManager.saveUser(newUser)
                                    registeredUsers.add(newUser)
                                    loggedInUser = newUser
                                    currentScreen = Screen.HOME
                                },
                                onNavigateToLogin = {
                                    currentScreen = Screen.LOGIN
                                }
                            )
                        }
                        Screen.HOME -> {
                            loggedInUser?.let { user ->
                                RouteDropScreen(
                                    loggedInUser = user,
                                    globalDeliveries = globalDeliveries,
                                    onLogout = {
                                        authManager.clearUser()
                                        loggedInUser = null
                                        currentScreen = Screen.LOGIN
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
