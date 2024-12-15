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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

@Composable
fun LikedPostsView(
    email: String,
    nickname: String,
    onBack: () -> Unit
) {
    // Получаем доступ к базе данных
    val database = FirebaseDatabase.getInstance().reference
    val storage = FirebaseStorage.getInstance().reference

    // Состояние для хранения списка изображений
    var likedImages by remember { mutableStateOf<List<String>>(emptyList()) }
    val auth = FirebaseAuth.getInstance()

    // Состояние для отображения выбранного изображения в полном экране
    var isImageOpen by remember { mutableStateOf(false) }
    var selectedImageUrl by remember { mutableStateOf("") }

    // Загружаем список изображений, которые лайкнул пользователь
    LaunchedEffect(email) {
        val likedRef = database.child("users").child(auth.currentUser?.uid ?: "").child("likes")
        likedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val images = mutableListOf<String>()
                for (imageSnapshot in snapshot.children) {
                    val imageUrl = imageSnapshot.getValue(String::class.java)
                    if (imageUrl != null) {
                        images.add(imageUrl)
                    }
                }
                likedImages = images
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LikedPostsView", "Failed to read liked images", error.toException())
            }
        })
    }

    // Отображаем список изображений в сетке
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize()
        ) {
            items(likedImages) { imageUrl ->
                ImageCard(
                    imageUrl = imageUrl,
                    onImageClick = {
                        selectedImageUrl = imageUrl
                        isImageOpen = true
                    }
                )
            }
        }

        if (isImageOpen) {
            FullScreenImage(
                imageUrl = selectedImageUrl,
                auth = auth,
                database = database,
                onClose = { isImageOpen = false }
            )
        }

        // Кнопка "Назад"
        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text("Назад")
        }
    }
}

@Composable
fun ImageCard(
    imageUrl: String,
    onImageClick: () -> Unit
) {
    val painter = rememberImagePainter(imageUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onImageClick() }
    ) {
        Image(
            painter = painter,
            contentDescription = "Liked Image",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp), // Ограничиваем максимальную высоту
            contentScale = ContentScale.Fit // Изображение будет полностью помещаться в контейнер
        )
    }
}

@Composable
fun FullScreenLike(
    imageUrl: String,
    auth: FirebaseAuth,
    database: DatabaseReference,
    onClose: () -> Unit
) {
    val painter = rememberImagePainter(imageUrl)

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset(0f, 0f)) }
    var isLiked by remember { mutableStateOf(false) } // Состояние для лайка

    val maxScale = 3f
    val minScale = 1f

    // Проверка, лайкнуто ли изображение
    val userId = auth.currentUser?.uid
    userId?.let {
        val likesRef = database.child("users").child(userId).child("likes")
        likesRef.get().addOnSuccessListener { snapshot ->
            val existingLikes = snapshot.children.mapNotNull { it.getValue(String::class.java) }
            isLiked = existingLikes.contains(imageUrl)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(
                onClick = { /* Пустое действие для блокировки кликов под слоем */ },
                indication = null, // Убираем эффект нажатия
                interactionSource = remember { MutableInteractionSource() } // Не сохраняем состояние
            )
    ) {
        Image(
            painter = painter,
            contentDescription = "Full screen image",
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Расчет нового масштаба
                        scale = (scale * zoom).coerceIn(minScale, maxScale)

                        // Обновление смещения с учетом панорамирования
                        if (scale == minScale) {
                            offset = Offset(0f, 0f)
                        } else {
                            val newOffsetX = offset.x + pan.x
                            val newOffsetY = offset.y + pan.y

                            // Ограничиваем смещение, чтобы изображение не выходило за пределы экрана
                            offset = Offset(newOffsetX.coerceIn(-100f, 100f), newOffsetY.coerceIn(-100f, 100f))
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

        // Кнопка "Лайк"
        Button(
            onClick = {
                userId?.let { uid ->
                    val likesRef = database.child("users").child(uid).child("likes")
                    if (isLiked) {
                        // Если лайк уже есть, удаляем его
                        likesRef.get().addOnSuccessListener { snapshot ->
                            // Находим элемент с изображением и удаляем его
                            val likeToRemove = snapshot.children.find { it.getValue(String::class.java) == imageUrl }
                            likeToRemove?.ref?.removeValue()
                                ?.addOnSuccessListener {
                                    Log.d("FullScreenImage", "Image unliked successfully")
                                    isLiked = false
                                }
                        }
                    } else {
                        // Если лайка нет, добавляем его
                        likesRef.push().setValue(imageUrl)
                            .addOnSuccessListener {
                                Log.d("FullScreenImage", "Image liked successfully")
                                isLiked = true
                            }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(if (isLiked) "Удалить лайк" else "Поставить лайк")
        }

        // Кнопка "Закрыть"
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Закрыть")
        }
    }
}
