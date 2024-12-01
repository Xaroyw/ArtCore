package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import coil.compose.rememberImagePainter
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale

@Composable
fun ProfileScreen(
    email: String,
    nickname: String,
    onLogout: () -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val storage: FirebaseStorage = FirebaseStorage.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")

    // Загрузка изображения профиля
    val uploadImage = { uri: Uri ->
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Сначала загружаем изображение в Firebase Storage
            val storageRef = storage.reference.child("profile_images").child(userId)
            storageRef.putFile(uri).addOnSuccessListener {
                // После успешной загрузки изображения получаем его URL
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    Log.d("ProfileScreen", "Download URL: $downloadUri")
                    // Сохраняем URL изображения в Firebase Database
                    database.child(userId).child("profileImageUrl").setValue(downloadUri.toString()).addOnSuccessListener {
                        Log.d("ProfileScreen", "New profile image URL saved successfully")
                        // Обновляем imageUri с новым URL
                        imageUri = downloadUri
                    }.addOnFailureListener {
                        Log.e("ProfileScreen", "Error saving new profile image URL: ${it.message}")
                    }
                }.addOnFailureListener {
                    Log.e("ProfileScreen", "Error getting download URL: ${it.message}")
                }
            }.addOnFailureListener {
                Log.e("ProfileScreen", "Error uploading profile image: ${it.message}")
            }
        }
    }

    // Получение URL изображения из базы данных
    LaunchedEffect(auth.currentUser?.uid) {
        auth.currentUser?.uid?.let { userId ->
            // Загружаем URL изображения из Firebase Realtime Database
            database.child(userId).child("profileImageUrl").get().addOnSuccessListener { snapshot ->
                val imageUrl = snapshot.value as? String
                Log.d("ProfileScreen", "Fetched profile image URL: $imageUrl")
                // Если URL существует, устанавливаем его
                imageUri = imageUrl?.let { Uri.parse(it) }
            }.addOnFailureListener {
                Log.e("ProfileScreen", "Error fetching profile image URL: ${it.message}")
            }
        }
    }

    // Запуск выбора изображения
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            uploadImage(uri)  // Загружаем выбранное изображение
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Отображение изображения профиля или картинки по умолчанию
        val painter: Painter = rememberImagePainter(
            data = imageUri ?: R.drawable.ic_default_avatar,  // Если imageUri null, используем картинку по умолчанию
            builder = {
                crossfade(true)
                memoryCacheKey(imageUri.toString())  // Устанавливаем уникальный ключ для кеша
            }
        )
        Image(
            painter = painter,
            contentDescription = "Profile Image",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape) // Обрезка изображения в круг
                .align(Alignment.CenterHorizontally)
                .background(MaterialTheme.colorScheme.background) // Добавляем фон
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) // Добавляем границу
                .aspectRatio(1f), // Устанавливаем соотношение сторон 1:1
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Email: $email")
        Text(text = "Nickname: $nickname")

        Spacer(modifier = Modifier.height(24.dp))

        // Кнопка выбора изображения
        Button(onClick = { pickImage.launch("image/*") }) {
            Text("Выбрать изображение")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка выхода
        Button(onClick = onLogout) {
            Text("Выйти")
        }
    }
}
