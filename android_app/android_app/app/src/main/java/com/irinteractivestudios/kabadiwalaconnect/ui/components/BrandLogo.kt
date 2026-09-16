package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KcTheme
import com.irinteractivestudios.kabadiwalaconnect.ui.theme.KabadiwalaConnectTheme

/** Keeps the dark-green brand mark legible on both light and dark surfaces. */
@Composable
fun KcBrandLogo(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp
) {
    Surface(
        color = KcTheme.extended.logoPlate,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        border = BorderStroke(1.dp, KcTheme.extended.logoPlateBorder),
        modifier = modifier
            .size(size)
            .shadow(8.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_kc_logo),
            contentDescription = contentDescription,
            modifier = Modifier.padding(size * .13f)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KcBrandLogoDarkPreview() {
    KabadiwalaConnectTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(20.dp)) {
            KcBrandLogo(contentDescription = "Kabadiwala Connect")
        }
    }
}
