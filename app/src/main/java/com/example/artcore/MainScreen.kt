package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToProfile: () -> Unit
) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var nickname by remember { mutableStateOf("Загрузка...") }
    var images by remember { mutableStateOf(listOf<Pair<String, String>>()) } // (Image URL, Uploader Nickname)

    // Загрузка данных пользователя
    LaunchedEffect(auth.currentUser?.uid) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            val userProfileRef = database.child("users").child(userId)

            userProfileRef.get().addOnSuccessListener { snapshot ->
                nickname = snapshot.child("nickname").getValue(String::class.java) ?: "Никнейм не найден"
                val updatedImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                imageUri = updatedImageUrl?.let { Uri.parse(it) }
                Log.d("MainScreen", "User profile loaded: $nickname, imageUri: $imageUri")
            }.addOnFailureListener {
                Log.e("MainScreen", "Failed to load user profile", it)
            }
        }

        // Загрузка изображений ленты
        val imagesRef = database.child("allImages") // Предполагается, что изображения находятся в "allImages"
        imagesRef.get().addOnSuccessListener { snapshot ->
            val fetchedImages = snapshot.children.mapNotNull { imageSnapshot ->
                val imageUrl = imageSnapshot.child("url").getValue(String::class.java)
                val uploaderNickname = imageSnapshot.child("nickname").getValue(String::class.java)
                if (imageUrl != null && uploaderNickname != null) imageUrl to uploaderNickname else null
            }

            // Логирование количества полученных изображений
            Log.d("MainScreen", "Fetched ${fetchedImages.size} images from database")

            // Случайная сортировка изображений
            images = fetchedImages.shuffled() // Перемешиваем изображения случайным образом
            Log.d("MainScreen", "Images shuffled, new order: ${fetchedImages.shuffled()}")
        }.addOnFailureListener {
            Log.e("MainScreen", "Failed to load images", it)
        }
    }

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
                                .clickable { onNavigateToProfile() },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = nickname)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(images) { (imageUrl, uploaderNickname) ->
                ImageCard(
                    imageUrl = imageUrl,
                    uploaderNickname = uploaderNickname
                )
            }
        }
    }
}

@Composable
fun ImageCard(
    imageUrl: String,
    uploaderNickname: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Image(
            painter = rememberImagePainter(data = imageUrl),
            contentDescription = "Uploaded Image",
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Опубликовал: $uploaderNickname",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
