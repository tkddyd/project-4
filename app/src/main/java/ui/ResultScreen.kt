package com.example.project_2.ui.result

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.project_2.domain.model.Place
import com.example.project_2.domain.model.RecommendationResult
import com.example.project_2.domain.model.WeatherInfo
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelOptions
import java.net.URLEncoder

@Composable
fun ResultScreen(
    rec: RecommendationResult,
    regionHint: String? = null   // ✅ 사용자가 입력했던 지역 (예: "광주 상무동")
) {
    Log.d("UI", "ResultScreen received ${rec.places.size} places (topPicks=${rec.topPicks.size})")
    rec.places.forEachIndexed { i, p ->
        Log.d("UI", "[$i] ${p.name} (${p.lat}, ${p.lng}) reason=${rec.gptReasons[p.id] ?: "없음"}")
    }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val labelById = remember { mutableStateMapOf<String, Label>() }
    val baseNameById = remember { mutableStateMapOf<String, String>() }
    var highlightedId by remember { mutableStateOf<String?>(null) }

    val selectedOrder = remember { mutableStateListOf<String>() }
    val selectedPlaces: List<Place> by remember(selectedOrder, rec.places) {
        derivedStateOf { selectedOrder.mapNotNull { id -> rec.places.find { it.id == id } } }
    }

    val topIds: Set<String> = remember(rec.topPicks) { rec.topPicks.map { it.id }.toSet() }

    val focusOn: (Place) -> Unit = { p ->
        kakaoMap?.let { map ->
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(p.lat, p.lng)))
            // 이전 하이라이트 원복
            highlightedId?.let { prevId ->
                val prevLabel = labelById[prevId]
                val base = baseNameById[prevId]
                if (prevLabel != null && base != null) prevLabel.setTexts(base)
            }
            // 지금 선택한 애 하이라이트
            val lbl = labelById[p.id]
            val base = baseNameById[p.id] ?: (if (topIds.contains(p.id)) "★ ${p.name}" else p.name)
            if (lbl != null) {
                val newText = if (base.startsWith("★ ")) base else "★ $base"
                lbl.setTexts(newText)
                highlightedId = p.id
            }
        }
    }

    val toggleSelect: (Place) -> Unit = { p ->
        if (selectedOrder.contains(p.id)) selectedOrder.remove(p.id) else selectedOrder.add(p.id)
        refreshSelectedBadgesOnLabels(labelById, baseNameById, selectedOrder)
    }

    val buildRoute: () -> Unit = route@{
        val map = kakaoMap ?: return@route
        if (selectedPlaces.isNotEmpty()) {
            val (centerLat, centerLng) = computeCenter(selectedPlaces)
            map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(centerLat, centerLng)))
        }
    }

    val context = LocalContext.current

    // 전체 스크롤
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 날씨
        item(key = "weather") {
            WeatherBanner(rec.weather)
        }

        // 지도
        item(key = "map") {
            AndroidView(
                factory = {
                    val mv = MapView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (context.resources.displayMetrics.heightPixels * 0.35).toInt()
                        )
                    }
                    mv.start(
                        object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {}
                            override fun onMapError(p0: Exception?) {
                                Log.e("UI", "Map error: ${p0?.message}", p0)
                            }
                        },
                        object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                kakaoMap = map
                                addMarkersAndStore(
                                    map = map,
                                    places = rec.places,
                                    topIds = topIds,
                                    labelById = labelById,
                                    baseNameById = baseNameById
                                )
                                rec.places.firstOrNull()?.let {
                                    map.moveCamera(
                                        CameraUpdateFactory.newCenterPosition(LatLng.from(it.lat, it.lng))
                                    )
                                }
                            }
                        }
                    )
                    mv
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }

        // 카테고리 Top
        if (rec.topPicks.isNotEmpty()) {
            item(key = "top_title") {
                Text(
                    "카테고리별 상위 추천",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "top_row") {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(rec.topPicks, key = { it.id }) { p ->
                        TopPickCard(
                            p = p,
                            reason = rec.gptReasons[p.id],
                            isSelected = selectedOrder.contains(p.id),
                            onView = { focusOn(p) },
                            onToggle = {
                                toggleSelect(p)
                                focusOn(p)
                            }
                        )
                    }
                }
            }
        }

        // 추천 장소 타이틀
        item(key = "list_title") {
            Text(
                "추천 장소",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 추천 장소 리스트
        items(rec.places, key = { it.id }) { p ->
            PlaceRow(
                p = p,
                reason = rec.gptReasons[p.id],
                isSelected = selectedOrder.contains(p.id),
                aiMarked = rec.aiTopIds.contains(p.id),
                catTop = topIds.contains(p.id),
                regionHint = regionHint,   // ✅ 지역 힌트 넘김
                onToggle = {
                    toggleSelect(p)
                    focusOn(p)
                }
            )
        }

        // 하단 액션
        item(key = "actions") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedOrder.clear()
                        refreshSelectedBadgesOnLabels(labelById, baseNameById, selectedOrder)
                        kakaoMap?.let { clearRoutePolyline(it) }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("선택 초기화") }

                Button(
                    onClick = { buildRoute() },
                    enabled = selectedOrder.size >= 2,
                    modifier = Modifier.weight(2f)
                ) { Text("루트 생성하기 (${selectedOrder.size}개)") }
            }
        }
    }
}

@Composable
private fun WeatherBanner(w: WeatherInfo?) {
    if (w == null) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(Modifier.padding(16.dp)) {
            Text("🌤  현재 날씨  ${w.condition}  •  ${"%.1f".format(w.tempC)}℃")
        }
    }
}

/**
 * 리스트 행: 가게명 오른쪽에 작은 "바로가기" / 아래 쪽에 추천이유, 오른쪽엔 추가/제거 + 배지
 * 지역 힌트가 있으면 검색어에 같이 붙여서 더 정확하게 검색
 */
@Composable
private fun PlaceRow(
    p: Place,
    reason: String?,
    isSelected: Boolean,
    aiMarked: Boolean,
    catTop: Boolean,
    regionHint: String? = null,
    onToggle: () -> Unit
) {
    val context = LocalContext.current

    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 🔹 가게명 오른쪽 작은 "바로가기"
                TextButton(
                    onClick = {
                        val query = buildNaverQuery(p, regionHint)  // ✅ 지역 + 이름 + 주소
                        val encoded = URLEncoder.encode(query, "UTF-8")
                        val url = "https://m.search.naver.com/search.naver?query=$encoded"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    ),
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "바로가기",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        supportingContent = {
            Column {
                if (!p.address.isNullOrBlank()) {
                    Text(p.address!!)
                }
                if (!reason.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "추천 이유: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (catTop) SmallBadge("카테고리 Top")
                    if (aiMarked) SmallBadge("AI 추천")
                }
                if (isSelected) {
                    OutlinedButton(
                        onClick = onToggle,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "제거",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
                        )
                    }
                } else {
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "추가",
                            fontSize = MaterialTheme.typography.labelMedium.fontSize
                        )
                    }
                }
            }
        }
    )
    Divider()
}

/** 상단 TopPick 카드 */
@Composable
private fun TopPickCard(
    p: Place,
    reason: String?,
    isSelected: Boolean,
    onView: () -> Unit,
    onToggle: () -> Unit
) {
    Surface(
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .widthIn(min = 240.dp)
            .padding(vertical = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = p.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                SmallBadge("카테고리 Top")
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = p.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!reason.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onView,
                    modifier = Modifier.weight(1f)
                ) { Text("보기") }
                if (isSelected) {
                    OutlinedButton(
                        onClick = onToggle,
                        modifier = Modifier.weight(1f)
                    ) { Text("제거") }
                } else {
                    Button(
                        onClick = onToggle,
                        modifier = Modifier.weight(1f)
                    ) { Text("추가") }
                }
            }
        }
    }
}

/** 작고 깔끔한 배지 */
@Composable
private fun SmallBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        tonalElevation = 1.dp
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            maxLines = 1
        )
    }
}

private fun addMarkersAndStore(
    map: KakaoMap,
    places: List<Place>,
    topIds: Set<String>,
    labelById: MutableMap<String, Label>,
    baseNameById: MutableMap<String, String>
) {
    labelById.clear()
    baseNameById.clear()

    val manager = map.getLabelManager() ?: return
    val layer = manager.layer ?: return
    layer.removeAll()

    Log.d("UI", "addMarkersAndStore: adding ${places.size} markers")
    places.forEach { p ->
        val base = if (topIds.contains(p.id)) "★ ${p.name}" else p.name
        val label = layer.addLabel(
            LabelOptions.from(LatLng.from(p.lat, p.lng))
                .setTexts(base)
        )
        label?.show()
        if (label != null) {
            labelById[p.id] = label
            baseNameById[p.id] = base
        }
    }
}

private fun refreshSelectedBadgesOnLabels(
    labelById: Map<String, Label>,
    baseNameById: Map<String, String>,
    selectedOrder: List<String>
) {
    // 기본으로 돌려놓고
    baseNameById.forEach { (id, base) -> labelById[id]?.setTexts(base) }
    // 선택 순번 적용
    selectedOrder.forEachIndexed { index, id ->
        val base = baseNameById[id]
        if (base != null) labelById[id]?.setTexts("[${index + 1}] $base")
    }
}

private fun clearRoutePolyline(map: KakaoMap) {
    val sm = map.shapeManager ?: return
    val layer = sm.layer ?: return
    layer.removeAll()
}

private fun computeCenter(selected: List<Place>): Pair<Double, Double> {
    val minLat = selected.minOf { it.lat }
    val maxLat = selected.maxOf { it.lat }
    val minLng = selected.minOf { it.lng }
    val maxLng = selected.maxOf { it.lng }
    val centerLat = (minLat + maxLat) / 2.0
    val centerLng = (minLng + maxLng) / 2.0
    return centerLat to centerLng
}

/**
 * 네이버 검색어 생성
 * 우선순위: 지역 힌트 -> 가게 이름 -> 주소
 */
private fun buildNaverQuery(place: Place, regionHint: String? = null): String {
    val parts = mutableListOf<String>()
    if (!regionHint.isNullOrBlank()) {
        parts += regionHint
    }
    parts += place.name
    if (!place.address.isNullOrBlank()) {
        parts += place.address!!
    }
    return parts.joinToString(" ")
}
