package de.thermo.lueftung

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs

/** Native OOXML workbook: raw data, event durations and an editable Excel chart. */
object ExcelHistoryExport {
    private fun xml(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").filter { it=='\n' || it=='\t' || it.code>=32 }
    private fun date(at:Long):Double=25569.0+Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Berlin")).toLocalDateTime().toInstant(ZoneOffset.UTC).toEpochMilli()/86400000.0
    private fun human(at:Long)=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss xxx",Locale.GERMAN).format(Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Berlin")))
    private fun cell(column:Int,row:Int,value:Any?):String {
        var n=column+1;var letter="";while(n>0) { n--;letter=('A'.code+n%26).toChar()+letter;n/=26 }
        val ref="$letter$row"
        return if(value is Number) "<c r=\"$ref\"><v>$value</v></c>" else
            "<c r=\"$ref\" t=\"inlineStr\"><is><t>${xml(value?.toString() ?: "")}</t></is></c>"
    }
    private fun sheet(rows:List<List<Any?>>,drawing:Boolean=false,dateColumns:Set<Int> = emptySet()):String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"16\" width=\"24\" customWidth=\"1\"/></cols><sheetData>")
        rows.forEachIndexed { index,values ->
            append("<row r=\"${index+1}\">")
            values.forEachIndexed { col,value ->
                var entry=cell(col,index+1,value)
                if(index==0) entry=entry.replace("<c r=","<c s=\"2\" r=")
                else if(col in dateColumns && value is Number) entry=entry.replace("<c r=","<c s=\"1\" r=")
                append(entry)
            };append("</row>")
        };append("</sheetData>")
        if(!drawing && rows.isNotEmpty()) append("<autoFilter ref=\"A1:${('A'.code+(rows.first().size-1).coerceAtLeast(0)).toChar()}${rows.size}\"/>")
        if(drawing) append("<drawing r:id=\"rId1\"/>")
        append("</worksheet>")
    }
    fun write(data:HistorySelection,output:OutputStream) {
        require(data.metrics.isNotEmpty())
        val metrics=data.metrics.sortedBy { it.ordinal }
        val overview=listOf(listOf("Thermo V11.26 · ${data.roomName}",if(data.demo) "DEMO / Testdaten enthalten" else "Aufzeichnung"),
            listOf("Zeitraum",human(data.start),human(data.end)),listOf("Auswahl",metrics.joinToString { "${it.label} (${it.unit})" }),
            listOf("Zeitbasis","Europe/Berlin; UTC-Zeit zusätzlich in Messwerten"),listOf("Lüftungsmarker","Start/Ende: Ereignismessung oder nächster Messwert (max. 15 Min Abstand). Fehlende Werte bleiben leer."),
            listOf("Hinweis","Erfasste Ereignisse belegen nur ihre angegebene Quelle; keine rückwirkend erfundenen Sensorwerte."))
        val samples=buildList<List<Any?>> {
            add(listOf("Zeit Europe/Berlin","UTC-Zeit")+metrics.map { "${it.label} (${it.unit})" }+listOf("Quelle"))
            data.samples.forEach { s -> add(listOf(date(s.at),Instant.ofEpochMilli(s.at).toString())+metrics.map { it.value(s) }+listOf(s.source)) }
        }
        val events=buildList<List<Any?>> {
            add(listOf("Ereignis","Start Europe/Berlin","Ende Europe/Berlin","Dauer Sekunden","Quelle")+metrics.map { "Start ${it.label} (${it.unit})" }+metrics.map { "Ende ${it.label} (${it.unit})" }+listOf("Dauer im Zeitraum Sekunden","Plan Minuten","Hinweis","Start UTC","Ende UTC"))
            data.events.forEach { e -> add(listOf(e.type,date(e.start),e.end?.let { date(it) },((e.end ?: data.end)-e.start).coerceAtLeast(0)/1000.0,e.source)+
                metrics.map { when(it) { HistoryMetric.TEMPERATURE->e.temperature;HistoryMetric.HUMIDITY->e.humidity;HistoryMetric.DEW_POINT->e.dewPoint } }+
                metrics.map { when(it) { HistoryMetric.TEMPERATURE->e.endTemperature;HistoryMetric.HUMIDITY->e.endHumidity;HistoryMetric.DEW_POINT->e.endDewPoint } }+
                listOf(((minOf(e.end ?: data.end,data.end)-maxOf(e.start,data.start)).coerceAtLeast(0))/1000.0,e.plannedMinutes,e.note+if(e.end==null) " · Noch offen, Dauer bis Exportzeitpunkt" else "",Instant.ofEpochMilli(e.start).toString(),e.end?.let { Instant.ofEpochMilli(it).toString() })) }
        }
        ZipOutputStream(output).use { zip ->
            fun put(path:String,text:String) { zip.putNextEntry(ZipEntry(path));zip.write(text.toByteArray(Charsets.UTF_8));zip.closeEntry() }
            put("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/drawings/drawing1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/><Override PartName="/xl/charts/chart1.xml" ContentType="application/vnd.openxmlformats-officedocument.drawingml.chart+xml"/></Types>""")
            put("_rels/.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml","""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Uebersicht" sheetId="1" r:id="rId1"/><sheet name="Messwerte" sheetId="2" r:id="rId2"/><sheet name="Ereignisse" sheetId="3" r:id="rId3"/></sheets><calcPr calcId="191029" fullCalcOnLoad="1"/></workbook>""")
            put("xl/_rels/workbook.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/><Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            put("xl/styles.xml","""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="dd.mm.yyyy hh:mm:ss"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            put("xl/worksheets/sheet1.xml",sheet(overview,true))
            put("xl/worksheets/sheet2.xml",sheet(samples,dateColumns=setOf(0)))
            put("xl/worksheets/sheet3.xml",sheet(events,dateColumns=setOf(1,2)))
            put("xl/worksheets/_rels/sheet1.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing1.xml"/></Relationships>""")
            put("xl/drawings/drawing1.xml","""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><xdr:twoCellAnchor><xdr:from><xdr:col>0</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>8</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from><xdr:to><xdr:col>12</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>33</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to><xdr:graphicFrame macro=""><xdr:nvGraphicFramePr><xdr:cNvPr id="2" name="Klimaverlauf"/><xdr:cNvGraphicFramePr/></xdr:nvGraphicFramePr><xdr:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/></xdr:xfrm><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/chart"><c:chart xmlns:c="http://schemas.openxmlformats.org/drawingml/2006/chart" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:id="rId1"/></a:graphicData></a:graphic></xdr:graphicFrame><xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>""")
            put("xl/drawings/_rels/drawing1.xml.rels","""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/chart" Target="../charts/chart1.xml"/></Relationships>""")
            put("xl/charts/chart1.xml",chart(data,metrics))
        }
    }
    private fun cache(values:List<Double>)="<c:numCache><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>"+values.mapIndexed { i,v -> "<c:pt idx=\"$i\"><c:v>$v</c:v></c:pt>" }.joinToString("")+"</c:numCache>"
    private fun literal(values:List<Double>)="<c:numLit><c:formatCode>General</c:formatCode><c:ptCount val=\"${values.size}\"/>"+values.mapIndexed { i,v -> "<c:pt idx=\"$i\"><c:v>$v</c:v></c:pt>" }.joinToString("")+"</c:numLit>"
    private fun title(text:String)="<c:title><c:tx><c:rich><a:bodyPr/><a:lstStyle/><a:p><a:r><a:t>${xml(text)}</a:t></a:r></a:p></c:rich></c:tx><c:overlay val=\"0\"/></c:title>"
    private fun chart(data:HistorySelection,metrics:List<HistoryMetric>):String=buildString {
        append("<c:chartSpace xmlns:c=\"http://schemas.openxmlformats.org/drawingml/2006/chart\" xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\"><c:lang val=\"de-DE\"/><c:chart>")
        append(title("${data.roomName} · ${human(data.start)} – ${human(data.end)}${if(data.demo) " · DEMO" else ""}"));append("<c:plotArea><c:layout/>")
        var index=0
        val groups=listOf(metrics.filter { it!=HistoryMetric.HUMIDITY } to false,metrics.filter { it==HistoryMetric.HUMIDITY } to true).filter { it.first.isNotEmpty() }
        groups.forEach { (group,secondary) ->
            val xId=if(secondary) 40 else 10;val yId=if(secondary) 30 else 20
            append("<c:scatterChart><c:scatterStyle val=\"lineMarker\"/><c:varyColors val=\"0\"/>")
            group.forEach { metric ->
                val column=('C'.code+metrics.indexOf(metric)).toChar()
                val color=metric.color.toString(16).takeLast(6).uppercase()
                append("<c:ser><c:idx val=\"$index\"/><c:order val=\"$index\"/><c:tx><c:v>${metric.label} (${metric.unit})</c:v></c:tx><c:spPr><a:ln w=\"25400\"><a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill></a:ln></c:spPr><c:marker><c:symbol val=\"none\"/></c:marker>")
                val last=(data.samples.size+1).coerceAtLeast(2)
                append("<c:xVal><c:numRef><c:f>Messwerte!\$A\$2:\$A\$$last</c:f>${cache(data.samples.map { date(it.at) })}</c:numRef></c:xVal>")
                append("<c:yVal><c:numRef><c:f>Messwerte!\$$column\$2:\$$column\$$last</c:f>${cache(data.samples.map { metric.value(it) })}</c:numRef></c:yVal><c:smooth val=\"0\"/></c:ser>")
                index++
            }
            // Mark actual event snapshots; nearest samples are only used within 15 minutes.
            if(group.first()==metrics.first()) {
                listOf("VENTILATION" to "Lüften","HEATING" to "Heizung").forEach { (type,label) ->
                    listOf(false,true).forEach { end ->
                        val metric=metrics.first()
                        val points=data.events.filter { (if(type=="HEATING") it.type.startsWith(type) else it.type==type) && (!end || it.end!=null) }.mapNotNull { event ->
                            val at=if(end) event.end!! else event.start
                            if(at !in data.start..data.end) return@mapNotNull null
                            val measured=when(metric) {
                                HistoryMetric.TEMPERATURE -> if(end) event.endTemperature else event.temperature
                                HistoryMetric.HUMIDITY -> if(end) event.endHumidity else event.humidity
                                HistoryMetric.DEW_POINT -> if(end) event.endDewPoint else event.dewPoint
                            }
                            val nearest=data.samples.minByOrNull { abs(it.at-at) }?.takeIf { abs(it.at-at)<=15*60000 }
                            val value=measured ?: nearest?.let { metric.value(it) } ?: return@mapNotNull null
                            at to value
                        }
                        if(points.isNotEmpty()) {
                            val color=if(type=="VENTILATION") "218C54" else "D47D18"
                            append("<c:ser><c:idx val=\"$index\"/><c:order val=\"$index\"/><c:tx><c:v>$label${if(end) "ende" else "start"} (Ereignis/naher Messwert)</c:v></c:tx><c:spPr><a:ln><a:noFill/></a:ln></c:spPr><c:marker><c:symbol val=\"${if(end) "triangle" else "circle"}\"/><c:size val=\"7\"/><c:spPr><a:solidFill><a:srgbClr val=\"$color\"/></a:solidFill></c:spPr></c:marker><c:xVal>${literal(points.map { date(it.first) })}</c:xVal><c:yVal>${literal(points.map { it.second })}</c:yVal></c:ser>")
                            index++
                        }
                    }
                }
            }
            append("<c:axId val=\"$xId\"/><c:axId val=\"$yId\"/></c:scatterChart>")
            // Axes are emitted after all chart groups below.
        }
        fun axis(id:Int,cross:Int,pos:String,caption:String,hidden:Boolean=false,humidity:Boolean=false) {
            append("<c:valAx><c:axId val=\"$id\"/><c:scaling><c:orientation val=\"minMax\"/>")
            if(pos=="b") { append("<c:max val=\"${date(data.end)}\"/><c:min val=\"${date(data.start)}\"/>") }
            if(humidity) { append("<c:max val=\"100\"/><c:min val=\"0\"/>") }
            append("</c:scaling><c:delete val=\"${if(hidden) 1 else 0}\"/><c:axPos val=\"$pos\"/>")
            if(caption.isNotBlank()) append(title(caption))
            append("<c:numFmt formatCode=\"${if(pos=="b") "dd.mm. hh:mm" else "0.0"}\" sourceLinked=\"0\"/><c:tickLblPos val=\"nextTo\"/><c:crossAx val=\"$cross\"/><c:crosses val=\"${if(pos=="r") "max" else "autoZero"}\"/><c:crossBetween val=\"midCat\"/></c:valAx>")
        }
        if(metrics.any { it!=HistoryMetric.HUMIDITY }) { axis(10,20,"b","Zeit Europe/Berlin");axis(20,10,"l","°C") }
        if(HistoryMetric.HUMIDITY in metrics) { axis(40,30,"b",if(metrics.size==1) "Zeit Europe/Berlin" else "",metrics.size>1);axis(30,40,"r","Feuchte (%)",humidity=true) }
        append("</c:plotArea><c:legend><c:legendPos val=\"b\"/><c:overlay val=\"0\"/></c:legend><c:plotVisOnly val=\"1\"/><c:dispBlanksAs val=\"gap\"/></c:chart></c:chartSpace>")
    }
}
