package ai.mwm.client.cloud

import org.json.JSONObject

internal data class SharedRecordPage(val rows:List<JSONObject>,val range:String?)
internal suspend fun loadRecordPages(fetch:suspend (Int)->SharedRecordPage):List<JSONObject> {
    val all=mutableListOf<JSONObject>()
    var offset=0
    while(true) {
        val page=fetch(offset)
        all.addAll(page.rows)
        if(recordPageComplete(page.range,offset,page.rows.size))return all.distinctBy {it.getString("id")}
        offset+=page.rows.size
    }
}

/** Honor the actual server count, including server row caps below our requested page size. */
internal fun recordPageComplete(contentRange:String?,offset:Int,size:Int):Boolean {
    if(size==0)return true
    val total=contentRange?.substringAfterLast('/')?.toLongOrNull() ?: return false
    return offset.toLong()+size>=total
}
