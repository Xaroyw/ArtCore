package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
    var showFullImage by remember { mutableStateOf(false) }

    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    val userId = auth.currentUser?.uid
    val userProfileRef = userId?.let { database.child(it) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                uploadImageToFirebase(uri, userId, storage, userProfileRef) { newImage ->
                    uploadedImages = uploadedImages + newImage
                }
            }
        }
    )

    LaunchedEffect(userId) {
        userId?.let {
            userProfileRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    currentNickname = snapshot.child("nickname").getValue(String::class.java) ?: nickname
                    profileImageUrl = snapshot.child("profileImageUrl").getValue(String::class.java)
                    uploadedImages = snapshot.child("uploadedImages").children
                        .mapNotNull { it.getValue(String::class.java) }
                }

                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val painter = rememberImagePainter(
                data = profileImageUrl ?: R.drawable.ic_default_avatar
            )

            // Profile Image
            Image(
                painter = painter,
                contentDescription = "Profile Image",
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Почта: $email", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Никнейм: $currentNickname", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { imagePickerLauncher.launch("image/*") }) {
                Text("Добавить изображение")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onEditProfile) {
                Text("Редактировать профиль")
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onBack) {
                Text("Назад")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Image Grid
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
                                selectedImageUrl = imageUrl
                                showFullImage = true
                            },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Show full image when clicked
        if (showFullImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
                    .zIndex(1f)
            ) {
                Image(
                    painter = rememberImagePainter(selectedImageUrl),
                    contentDescription = "Full size image",
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                Button(
                    onClick = { showFullImage = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(2f)
                ) {
                    Text("Закрыть")
                }

                // Удалить кнопку не используется для аватарки
                Button(onClick = {
                    if (selectedImageUrl != profileImageUrl) {
                        deleteImageFromFirebase(selectedImageUrl, userId, storage, userProfileRef) { remainingImages ->
                            uploadedImages = remainingImages
                        }
                    } else {
                        // Аватар не удаляем
                        Log.d("ProfileScreen", "Attempted to delete avatar, but it cannot be deleted.")
                    }
                    showFullImage = false // Закрыть полноэкранное изображение после удаления
                },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .zIndex(1f)  // Это гарантирует, что кнопка будет сверху
                ) {
                    Text("Удалить")
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
    if (userId == null) return

    val timestamp = System.currentTimeMillis()
    val storageRef = storage.reference.child("users/$userId/uploadedImages/$timestamp.jpg")

    // Логируем URL изображения перед загрузкой
    Log.d("ProfileScreen", "Uploading image: ${uri.toString()}")

    storageRef.putFile(uri)
        .addOnSuccessListener {
            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                val imageUrl = downloadUri.toString()
                // Логируем URL после загрузки
                Log.d("ProfileScreen", "Image uploaded successfully. URL: $imageUrl")
                userProfileRef?.child("uploadedImages")?.push()?.setValue(imageUrl)
                onImageUploaded(imageUrl)
            }
        }
        .addOnFailureListener { e ->
            Log.e("ProfileScreen", "Image upload failed: ${e.message}")
        }
}

private fun deleteImageFromFirebase(
    imageUrl: String?,
    userId: String?,
    storage: FirebaseStorage,
    userProfileRef: DatabaseReference?,
    onImageDeleted: (List<String>) -> Unit
) {
    if (imageUrl == null || userId == null) return

    // Логируем URL изображения перед удалением
    Log.d("ProfileScreen", "Deleting image: $imageUrl for user: $userId")

    // Декодирование URL для получения правильного пути
    val decodedUrl = Uri.parse(imageUrl)?.path?.replace("%2F", "/")
    val storageRef = storage.reference.child("users/$userId/uploadedImages/${Uri.parse(decodedUrl)?.lastPathSegment}")

    // Проверка существования файла перед удалением
    storageRef.metadata.addOnSuccessListener {
        storageRef.delete()
            .addOnSuccessListener {
                // Логируем успешное удаление изображения из Storage
                Log.d("ProfileScreen", "Image deleted from Storage")

                // Удаление изображения из Realtime Database
                userProfileRef?.child("uploadedImages")
                    ?.orderByValue()
                    ?.equalTo(imageUrl)
                    ?.limitToFirst(1)
                    ?.get()
                    ?.addOnSuccessListener { snapshot ->
                        snapshot.children.firstOrNull()?.ref?.removeValue()
                        Log.d("ProfileScreen", "Image deleted from Database")

                        // Обновление UI с оставшимися изображениями
                        userProfileRef?.child("uploadedImages")?.get()
                            ?.addOnSuccessListener { remainingSnapshot ->
                                val remainingImages = remainingSnapshot.children
                                    .mapNotNull { it.getValue(String::class.java) }
                                onImageDeleted(remainingImages)
                            }
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileScreen", "Error deleting image: ${e.message}")
            }
    }.addOnFailureListener {
        // Файл не существует
        Log.e("ProfileScreen", "File does not exist at the specified location.")
        // Обработайте ошибку, например, уведомив пользователя, что файл не найден
    }
}

