package de.thermo.lueftung

data class VentilationPlan(val minutes:Int?,val explanation:String,val canVentilate:Boolean)
object VentilationPlanner {
    fun plan(insideT:Double,insideRh:Double,outsideT:Double,outsideRh:Double,basement:Boolean,wallT:Double?,crossVentilation:Boolean=false):VentilationPlan {
        val minTemperature=if(basement) 14.0 else 17.0
        if(insideT<=minTemperature) return VentilationPlan(null,"Raum bereits zu kalt: zusätzliches Auskühlen und Wiederaufheizen vermeiden.",false)
        val advice=DryingAdvice.ventilation(insideT,insideRh,outsideT,outsideRh,basement,wallT)
        if(advice!=VentilationAdvice.DRYING_POTENTIAL) return VentilationPlan(null,when(advice) {
            VentilationAdvice.NO_DRYING_POTENTIAL -> "Außenluft bietet derzeit kein ausreichendes Trocknungspotenzial."
            VentilationAdvice.WALL_MEASUREMENT_REQUIRED -> "Im Keller zuerst die aktuelle Temperatur der kältesten Wand messen."
            else -> "Außentaupunkt zu hoch gegenüber der Wand: keine Trocknungslüftung empfohlen."
        },false)
        val delta=insideT-outsideT
        val minutes=when { delta>=12 -> 3; delta>=6 -> 5; delta>=0 -> 8; else -> 10 }
        return VentilationPlan(if(crossVentilation) (minutes/2).coerceAtLeast(2) else minutes,
            if(delta>=12) "Kurzes Stoßlüften: große Temperaturdifferenz, Wärmeverluste begrenzen." else
                "Außenluft kann Feuchte abführen. Dauer ist eine Schätzung; Raumtemperatur und Fensterzustand beobachten.",true)
    }
}
data class ActiveVentilationTimer(val eventId:Long,val roomId:String,val started:Long,val deadline:Long,val source:String,val expired:Boolean=false,val elapsedDeadline:Long?=null,val boot:Int?=null) {
    fun remaining(now:Long,elapsed:Long?=null,currentBoot:Int?=null):Int {
        val left=if(elapsed!=null && elapsedDeadline!=null && boot!=null && boot==currentBoot) elapsedDeadline-elapsed else deadline-now
        return ((left+999)/1000).coerceIn(0,Int.MAX_VALUE.toLong()).toInt()
    }
}
