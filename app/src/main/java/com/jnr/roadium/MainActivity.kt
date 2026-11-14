package com.jnr.roadium

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.jnr.roadium.ui.RoadiumRoot
import com.jnr.roadium.ui.RoadiumTheme

class MainActivity : ComponentActivity() {
    private val controller get() = (application as RoadiumApp).controller

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // 권한 결과와 관계없이 시작한다 (권한이 없는 SDK는 '없음'으로 표시되고 대체 동작)
            controller.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RoadiumTheme { RoadiumRoot(controller) } }

        // 기획서: 뒤로 가기 입력 시 앱을 종료하지 않고 숨김 처리 → 다른 앱(내비 등)을 보고 있어도 중계는 이어진다
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val ui = controller.ui.value
                if (ui.recap != null) controller.closeRecap() else moveTaskToBack(true)
            }
        })

        val wanted = buildList {
            add("pleos.car.permission.CAR_DRIVE")
            add("pleos.car.permission.TTS_SERVICE")
            add("pleos.car.permission.STT_SERVICE")
            add("pleos.car.permission.NAVI_ROUTE")
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) controller.start() else permissionLauncher.launch(wanted.toTypedArray())
    }

    override fun onStart() {
        super.onStart()
        controller.start()
    }

    override fun onStop() {
        super.onStop()
        // 예약·중계가 없으면 SDK와 타이머를 해제해 프로세스가 cached 상태로 내려가게 한다
        if (!isChangingConfigurations) controller.onUiHidden()
    }
}
