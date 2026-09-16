package com.narvyn.suraksha

/** Positions relative to one confirmed printed reference, in metres. Not safety distances. */
object TrainingBayLayout {
    data class Station(val x: Float,val z: Float)
    val stations = listOf(Station(-.75f,0f),Station(.55f,-.50f),Station(.70f,1.0f))
    const val IMAGE_WIDTH_METRES=.27f
    const val IMAGE_NAME="suraksha-training-bay-v1"
    fun valid(module: String):Boolean {
        val accepted=mutableListOf<RoomStationPlacement.Station>()
        for(p in stations) {
            val next=RoomStationPlacement.Station(p.x,p.z)
            if(!RoomStationPlacement.evaluate(module,accepted,next).allowed)return false
            accepted.add(next)
        }
        return true
    }
}
