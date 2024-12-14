package com.example.artcore

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

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
    var imageToUpload by remember { mutableStateOf<Uri?>(null) }
    var showNewImagePreview by remember { mutableStateOf(false) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }

    val maxScale = 3f
    val minScale = 1f

    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    val database = FirebaseDatabase.getInstance().reference.child("users")
    val storage: FirebaseStorage = FirebaseStorage.getInstance()

    val userId = auth.currentUser?.uid
    val userProfileRef = userId?.let { database.child(it) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                imageToUpload = uri
                showNewImagePreview = true
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
        Column(modifier = Modifier.align(Alignment.TopCenter)) {
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

        // Full image display logic
        if (showFullImage && selectedImageUrl != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
                    .zIndex(1f)
                    .clickable(
                        onClick = { /* empty action */ },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    )
            ) {
                Image(
                    painter = rememberImagePainter(selectedImageUrl),
                    contentDescription = "Full size image",
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // Расчет нового масштаба
                                scale = (scale * zoom).coerceIn(minScale, maxScale)

                                // Обновление смещения с учетом панорамирования, удерживая изображение в пределах экрана
                                if (scale == minScale) {
                                    offset = Offset(0f, 0f)
                                } else {
                                    val newOffsetX = offset.x + pan.x
                                    val newOffsetY = offset.y + pan.y

                                    // Ограничиваем смещение, чтобы изображение не выходило за пределы экрана
                                    val maxOffsetX = (size.width * scale - size.width) / 2
                                    val maxOffsetY = (size.height * scale - size.height) / 2

                                    offset = Offset(
                                        x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                        y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                    )
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentScale = ContentScale.Fit
                )

                Button(
                    onClick = {
                        showFullImage = false
                        scale = 1f // Reset scale
                        offset = Offset(0f, 0f) // Reset offset
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .zIndex(2f)
                ) {
                    Text("Закрыть")
                }
                Button(onClick = {
                    if (selectedImageUrl != profileImageUrl) {
                        deleteImageFromFirebase(selectedImageUrl, userId, storage, userProfileRef, currentNickname) { remainingImages ->
                            uploadedImages = remainingImages
                        }
                    }
                    showFullImage = false // Закрыть полноэкранное изображение после удаления
                },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .zIndex(1f)
                ) {
                    Text("Удалить")
                }
            }
        }

        // Full screen preview and upload section for new image
        if (showNewImagePreview && imageToUpload != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
                    .zIndex(2f)
            ) {
                Image(
                    painter = rememberImagePainter(imageToUpload),
                    contentDescription = "Preview Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentScale = ContentScale.Fit
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { showNewImagePreview = false },
                        modifier = Modifier.fillMaxWidth(0.45f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            imageToUpload?.let { uri ->
                                uploadImageToFirebase(uri, userId, storage, userProfileRef, currentNickname) { newImage ->
                                    uploadedImages = uploadedImages + newImage
                                }
                                showNewImagePreview = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.45f)
                    ) {
                        Text("+")
                    }
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
    nickname: String,
    onImageUploaded: (String) -> Unit
) {
    if (userId == null) return

    val timestamp = System.currentTimeMillis()
    val uniqueImageKey = FirebaseDatabase.getInstance().reference.child("allImages").push().key ?: timestamp.toString()

    // User-specific folder including the nickname
    val userStorageRef = storage.reference.child("users/$nickname/uploadedImages/$uniqueImageKey.jpg")
    // Common images folder
    val allImagesStorageRef = storage.reference.child("allImages/$uniqueImageKey.jpg")

    // Log URI for debugging
    Log.d("ProfileScreen", "Uploading image: ${uri.toString()}")

    // Upload image to user-specific folder
    userStorageRef.putFile(uri)
        .addOnSuccessListener {
            userStorageRef.downloadUrl.addOnSuccessListener { userDownloadUri ->
                val userImageUrl = userDownloadUri.toString()
                Log.d("ProfileScreen", "Image uploaded to user folder: $userImageUrl")

                // Save the image URL in the database
                userProfileRef?.child("uploadedImages")?.push()?.setValue(userImageUrl)

                // Notify UI with the uploaded image URL
                onImageUploaded(userImageUrl)

                // Upload to the common folder
                allImagesStorageRef.putFile(uri)
                    .addOnSuccessListener {
                        allImagesStorageRef.downloadUrl.addOnSuccessListener { allImagesDownloadUri ->
                            val allImageUrl = allImagesDownloadUri.toString()
                            Log.d("ProfileScreen", "Image uploaded to allImages folder: $allImageUrl")

                            // Save in the database with nickname and unique key
                            val imageData = mapOf(
                                "imageUrl" to allImageUrl,
                                "nickname" to nickname
                            )
                            FirebaseDatabase.getInstance().reference
                                .child("allImages")
                                .child(uniqueImageKey)  // Use unique key here
                                .setValue(imageData)
                        }
                    }
            }
        }
        .addOnFailureListener { e ->
            Log.e("ProfileScreen", "Failed to upload image: ${e.message}")
        }
}
private fun deleteImageFromFirebase(
    imageUrl: String?,
    userId: String?,
    storage: FirebaseStorage,
    userProfileRef: DatabaseReference?,
    nickname: String,
    onImageDeleted: (List<String>) -> Unit
) {
    if (imageUrl == null || userId == null) return

    // Логируем URL изображения и ID пользователя
    Log.d("ProfileScreen", "Deleting image: $imageUrl for user: $userId")

    // Декодируем URL изображения и получаем имя файла (уникальный ключ)
    val decodedUrl = Uri.parse(imageUrl)?.path?.replace("%2F", "/")
    val imageFileName = Uri.parse(decodedUrl)?.lastPathSegment

    // Используем уникальный ключ для поиска
    val uniqueImageKey = imageFileName?.substringBefore(".jpg") ?: return

    // Получаем ссылки на изображения в папках Firebase Storage
    val userStorageRef = storage.reference.child("users/$nickname/uploadedImages/$uniqueImageKey.jpg")
    val allImagesStorageRef = storage.reference.child("allImages/$uniqueImageKey.jpg")

    // Удаляем изображение из папки пользователя в Firebase Storage
    userStorageRef.delete()
        .addOnSuccessListener {
            Log.d("ProfileScreen", "Image deleted from user's folder")

            // Удаляем ссылку на изображение из базы данных пользователя
            userProfileRef?.child("uploadedImages")
                ?.orderByValue()
                ?.equalTo(imageUrl)
                ?.limitToFirst(1)
                ?.get()
                ?.addOnSuccessListener { snapshot ->
                    snapshot.children.firstOrNull()?.ref?.removeValue()
                    Log.d("ProfileScreen", "Image reference deleted from user's database uploadedImages")
                }
        }
        .addOnFailureListener { e ->
            Log.e("ProfileScreen", "Error deleting image from user's folder: ${e.message}")
        }

    // Удаляем изображение из общей папки в Firebase Storage
    allImagesStorageRef.delete()
        .addOnSuccessListener {
            Log.d("ProfileScreen", "Image deleted from allImages folder")

            // Удаляем ссылку на изображение из базы данных всех изображений по уникальному ключу
            FirebaseDatabase.getInstance().reference
                .child("allImages")
                .child(uniqueImageKey)  // Используем уникальный ключ
                .removeValue()
                .addOnSuccessListener {
                    Log.d("ProfileScreen", "Image reference deleted from allImages database")
                }
        }
        .addOnFailureListener { e ->
            Log.e("ProfileScreen", "Error deleting image from allImages folder: ${e.message}")
        }
}
