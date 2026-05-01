package com.fbint.collector.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.fbint.collector.ui.nav.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RolePickerScreen(nav: NavHostController) {
    Scaffold(topBar = { TopAppBar(title = { Text("Ahlan VOC — Choose role") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Pick the role for this device. Admin only needs to run once per project; surveyors scan the QR the admin generates.",
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { nav.navigate(Routes.ADMIN_SETUP) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Admin — set up the project") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { nav.navigate(Routes.SURVEYOR_SCAN) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Surveyor — scan setup QR") }
        }
    }
}
