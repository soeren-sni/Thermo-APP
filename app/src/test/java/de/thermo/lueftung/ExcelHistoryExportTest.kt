package de.thermo.lueftung

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExcelHistoryExportTest {
    private fun selection(metrics:Set<HistoryMetric> = HistoryMetric.entries.toSet())=HistorySelection("bath","Bad & Test",1700000000000,1700003600000,metrics,
        listOf(ClimateSample(1700000000000,22.0,70.0,16.0,"Demo"),ClimateSample(1700003600000,20.0,50.0,9.0,"Demo")),
        listOf(ClimateEvent(1,"bath","VENTILATION",1700000600000,1700000900000,"Demo",5,22.0,70.0,16.0,"Fenster <offen>",20.0,50.0,9.0)),true)
    private fun parts(data:HistorySelection):Map<String,String> {
        val out=ByteArrayOutputStream();ExcelHistoryExport.write(data,out)
        return ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> buildMap { while(true) { val entry=zip.nextEntry ?: break;put(entry.name,zip.readBytes().toString(Charsets.UTF_8)) } } }
    }
    @Test fun workbookIsValidXmlAndContainsEditableChartRawDataAndEventDuration() {
        val data=parts(selection())
        data.forEach { (_,xml) -> DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray())) }
        val chart=data.getValue("xl/charts/chart1.xml")
        assertTrue(chart.contains("Feuchte (%)"));assertTrue(chart.contains("Taupunkt (°C)"));assertTrue(chart.contains("Lüftenstart"));assertTrue(chart.contains("Lüftenende"))
        assertTrue(chart.contains("Messwerte!\$A\$2:\$A\$3"));assertTrue(chart.contains("DEMO"))
        assertTrue(data.getValue("xl/worksheets/sheet3.xml").contains("<v>300.0</v>"))
        assertTrue(data.getValue("xl/worksheets/sheet3.xml").contains("Fenster &lt;offen&gt;"))
    }
    @Test fun unselectedMetricsAreExcludedEverywhere() {
        val data=parts(selection(setOf(HistoryMetric.HUMIDITY)))
        assertFalse(data.values.any { it.contains("Taupunkt") || it.contains("Temperatur") })
        assertTrue(data.getValue("xl/charts/chart1.xml").contains("Feuchte (%)"))
    }
    @Test fun missingMeasurementsRemainBlankRatherThanBeingSynthesized() {
        val data=parts(selection().copy(samples=emptyList(),events=listOf(ClimateEvent(1,"bath","VENTILATION",1700000000000,null,"Manuell"))))
        assertFalse(data.getValue("xl/charts/chart1.xml").contains("Lüftenstart"))
        assertTrue(data.getValue("xl/worksheets/sheet3.xml").contains("Noch offen"))
    }
    @Test fun fixtureForIndependentSpreadsheetReader() {
        val file=File("build/validation/Thermo_V11.26_Bad_DEMO.xlsx");file.parentFile!!.mkdirs()
        file.outputStream().use { ExcelHistoryExport.write(selection(),it) };assertTrue(file.length()>1000)
    }
}
