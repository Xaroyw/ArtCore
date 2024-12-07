package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

@Composable
fun ProfileScreen(
    email: String,
    nickname: String,
    onEditProfile: () -> Unit
) {
    var currentNickname by remember { mutableStateOf(nickname) }
    val imageUri = remember { mutableStateOf<Uri?>(null) }
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    // Слушатель для изменений профиля в Firebase
    LaunchedEffect(auth.currentUser?.uid) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userProfileRef = database.child(userId)

            // Слушаем изменения данных в базе данных
            userProfileRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    // Обновляем данные после изменений
                    val updatedNickname = snapshot.child("nickname").getValue(String::class.java)
                    val updatedImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)

                    // Обновляем UI
                    updatedNickname?.let {
                        currentNickname = it
                    }
                    updatedImageUrl?.let {
                        imageUri.value = Uri.parse(it)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileScreen", "Error fetching data: ${error.message}")
                }
            })
        }
    }

    val painter = rememberImagePainter(
        data = imageUri.value ?: R.drawable.ic_default_avatar, // Загружаем либо из URL, либо дефолтное изображение
        builder = { crossfade(true) }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painter,
            contentDescription = "Profile Image",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Почта
        Text(text = "Почта: $email", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // Никнейм
        Text(text = "Никнейм: $currentNickname", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка для перехода на страницу редактирования профиля
        Button(onClick = onEditProfile) {
            Text("Редактировать профиль")
        }
    }
}

