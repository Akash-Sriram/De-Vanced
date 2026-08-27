package app.morphe.patches.googlephotos.misc.features

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.shared.compat.AppCompatibilities
import app.morphe.util.inputStreamFromBundledResource

val phenotypeAssetsPatch = resourcePatch(
    name = "Phenotype assets",
    description = "Bundles official Google Photos Phenotype flags into the APK assets.",
) {
    compatibleWith(AppCompatibilities.GOOGLE_PHOTOS, AppCompatibilities.MORPHE_PHOTOS)

    execute {
        val destFile = this["assets/phenotype/com.google.android.apps.photos.phenotype.xml"]
        destFile.parentFile?.mkdirs()
        val inputStream = inputStreamFromBundledResource("phenotype", "com.google.android.apps.photos.phenotype.xml")
            ?: throw IllegalStateException("Bundled phenotype XML not found in patch resources")
        inputStream.use { src ->
            destFile.outputStream().use { dst ->
                src.copyTo(dst)
            }
        }
    }
}
