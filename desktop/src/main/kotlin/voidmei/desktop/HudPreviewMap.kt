package voidmei.desktop

import voidmei.telemetry.*

/** Local schematic sample; never fetched from a live game. */
internal fun hudPreviewMap() = MapSnapshot(
    MapBounds(MapPoint(0.0, 0.0), MapPoint(20000.0, 20000.0), 1,
        MapPoint(2000.0, 2000.0), MapPoint(0.0, 0.0)),
    listOf(
        MapObject("aircraft", "Player", 0xFFFF00, MapPoint(.5, .5), MapPoint(0.0, -1.0), null, null, null),
        MapObject("aircraft", "Fighter", 0x4488FF, MapPoint(.65, .3), MapPoint(-1.0, 0.0), null, null, null),
        MapObject("airfield", null, 0x44FF88, null, null, MapPoint(.2, .7), MapPoint(.35, .7), null)))
