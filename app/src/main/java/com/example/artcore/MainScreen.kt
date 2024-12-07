package com.example.artcore

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)  // Отключаем предупреждение
@Composable
fun MainScreen(
    onNavigateToProfile: () -> Unit
) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var nickname by remember { mutableStateOf("Загрузка...") }

    // Загрузка данных пользователя
    LaunchedEffect(auth.currentUser?.uid) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userProfileRef = database.child(userId)

            userProfileRef.get().addOnSuccessListener { snapshot ->
                nickname = snapshot.child("nickname").getValue(String::class.java) ?: "Никнейм не найден"
                val updatedImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                imageUri = updatedImageUrl?.let { Uri.parse(it) }
            }
        }
    }

    // Состояние прокрутки
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = rememberImagePainter(
                                data = imageUri ?: R.drawable.ic_default_avatar
                            ),
                            contentDescription = "User Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { onNavigateToProfile() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = nickname)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // Здесь можно добавить контент основной страницы
            Spacer(modifier = Modifier.height(20.dp))
            Text("Основной контент страницы")
            // Можно добавить больше контента для прокрутки
        }
    }
}
