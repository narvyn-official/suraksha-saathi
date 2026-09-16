package com.narvyn.suraksha

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session
import org.junit.Assert.*
import org.junit.Test

/** Asset compilation only; does not prove camera detection, pose accuracy or learning. */
class TrainingBayAssetTest {
    @Test fun compileBundledReferenceDatabase() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=context.assets.open("ar/training-bay/reference.png").use {
            BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply{inSampleSize=2})
        }!!
        val session=Session(context)
        try {
            val database=AugmentedImageDatabase(session)
            assertEquals(0,database.addImage("suraksha-training-bay-v1",bitmap,.27f))
            assertEquals(1,database.numImages)
            context.openFileOutput("training-bay.imgdb",0).use{database.serialize(it)}
            context.openFileInput("training-bay.imgdb").use {
                assertEquals(1,AugmentedImageDatabase.deserialize(session,it).numImages)
            }
        } finally { bitmap.recycle();session.close() }
    }
}
