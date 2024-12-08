package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.rememberImagePainter
import coil.request.CachePolicy
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    email: String,
    nickname: String,
    onEditProfile: () -> Unit,
    onBack: () -> Unit
) {
    var currentNickname by remember { mutableStateOf(nickname) }
    var uploadedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var showFullImage by remember { mutableStateOf(false) } // Новый state для полноэкранного отображения изображений

    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    val userId = auth.currentUser?.uid
    val userProfileRef = userId?.let { database.child(it) }

    // Запуск выбора изображения
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            Log.d("ProfileScreen", "Image selected: $uri")
            if (uri != null) {
                uploadImageToFirebase(uri, userId, storage, userProfileRef) { newImage ->
                    uploadedImages = uploadedImages + newImage
                    Log.d("ProfileScreen", "New image uploaded: $newImage")
                }
            } else {
                Log.e("ProfileScreen", "No image selected")
            }
        }
    )

    // Слушатель изменений в профиле
    LaunchedEffect(userId) {
        userId?.let {
            userProfileRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentNickname = snapshot.child("nickname").getValue(String::class.java) ?: nickname
                    profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    uploadedImages = snapshot.child("uploadedImages").children
                        .mapNotNull { it.getValue(String::class.java) }

                    Log.d("ProfileScreen", "Profile data updated: $currentNickname, profileImageUrl: $profileImageUrl")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileScreen", "Error fetching data: ${error.message}")
                }
            })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Аватарка
        val painter: Painter = rememberImagePainter(
            data = profileImageUrl ?: R.drawable.ic_default_avatar,
            builder = {
                crossfade(true)
                diskCachePolicy(CachePolicy.ENABLED)
            }
        )

        // Логируем ошибки загрузки изображений
        LaunchedEffect(profileImageUrl) {
            Log.d("ProfileScreen", "Loading image: $profileImageUrl")
        }

        Image(
            painter = painter,
            contentDescription = "Profile Image",
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable {
                    // Открываем изображение в полном размере
                    selectedImageUrl = profileImageUrl
                    showFullImage = true
                }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Почта: $email", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Никнейм: $currentNickname", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка добавить изображение
        Button(onClick = { imagePickerLauncher.launch("image/*") }) {
            Text("Добавить изображение")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка редактировать профиль
        Button(onClick = onEditProfile) {
            Text("Редактировать профиль")
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка назад
        Button(onClick = onBack) {
            Text("Назад")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Сетка добавленных изображений
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(uploadedImages.size) { index ->
                val imageUrl = uploadedImages[index]
                val imagePainter = rememberImagePainter(data = imageUrl)

                Image(
                    painter = imagePainter,
                    contentDescription = "Uploaded Image",
                    modifier = Modifier
                        .padding(4.dp)
                        .size(100.dp)
                        .clickable {
                            // Открываем изображение в полном размере
                            selectedImageUrl = imageUrl
                            showFullImage = true
                        },
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Показываем изображение в полном размере
        if (showFullImage) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberImagePainter(
                        data = selectedImageUrl,
                        builder = {
                            crossfade(true)
                            diskCachePolicy(CachePolicy.DISABLED)
                        }
                    ),
                    contentDescription = "Full size image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .zIndex(1f),  // Убедитесь, что изображение отображается поверх других элементов
                    contentScale = ContentScale.Fit
                )
                // Кнопка для закрытия полноэкранного режима
                Button(
                    onClick = { showFullImage = false },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

private fun uploadImageToFirebase(
    uri: Uri,
    userId: String?,
    storage: FirebaseStorage,
    userProfileRef: DatabaseReference?,
    onImageUploaded: (String) -> Unit
) {
    if (userId == null) {
        Log.e("ProfileScreen", "User ID is null, cannot upload image")
        return
    }

    val timestamp = System.currentTimeMillis()
    val storageRef = storage.reference.child("users/$userId/uploadedImages/$timestamp.jpg")

    Log.d("ProfileScreen", "Uploading image to: $storageRef")

    storageRef.putFile(uri)
        .addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val imageUrl = downloadUri.toString()
                userProfileRef?.child("uploadedImages")?.push()?.setValue(imageUrl)
                onImageUploaded(imageUrl)
            }.addOnFailureListener { exception ->
                Log.e("ProfileScreen", "Error getting download URL: ${exception.message}")
            }
        }
        .addOnFailureListener { exception ->
            Log.e("ProfileScreen", "Error uploading image: ${exception.message}")
        }
}
