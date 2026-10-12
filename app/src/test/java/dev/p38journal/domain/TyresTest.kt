package dev.p38journal.domain

import dev.p38journal.data.TyreEvent
import org.junit.Assert.*
import org.junit.Test

class TyresTest {
    private fun event(id:String, axis:String="front", kind:String="mount", km:Int=175000, interval:Int=10000, sequence:Long=1) =
        TyreEvent(id=id,axis=axis,kind=kind,km=km,intervalKm=interval,sequence=sequence)

    @Test fun noMountDoesNotFabricateTyresAtThePlanningBaseline() {
        val states=Tyres.evaluate(emptyList(),175000)
        assertEquals(2,states.size)
        assertTrue(states.all { it.mount==null && it.dueKm==null && it.usedKm==null })
    }

    @Test fun reviewKeepsUsageAndSetsExactNextLimit() {
        val state=Tyres.evaluate(listOf(event("mount"),event("review",kind="review",km=185200,sequence=2)),185200).first()
        assertEquals(10200,state.usedKm)
        assertEquals(195200,state.dueKm)
        assertEquals(10000,state.remainingKm)
        assertEquals(175000,state.mount!!.km)
    }

    @Test fun replacingFrontNeverResetsRearAndCorrectionsFollowTheRecordedSequence() {
        val events=listOf(event("front-old"),event("rear-old",axis="rear"),event("front-new",kind="change",km=185000,sequence=2))
        val states=Tyres.evaluate(events,190000)
        assertEquals(5000,states[0].usedKm)
        assertEquals(15000,states[1].usedKm)
        assertEquals("front-new",Tyres.evaluate(events.map { if(it.id=="front-old")it.copy(km=186000)else it },190000)[0].mount!!.id)
        assertEquals(187000,Tyres.evaluate(events.map { if(it.id=="front-new")it.copy(km=177000)else it },190000)[0].dueKm)
    }

    @Test fun voidCorrectionUnknownOdometerAndOverflowAreExplicit() {
        val base=event("mount")
        val review=event("review",kind="review",km=185200,sequence=2)
        assertEquals(185000,Tyres.evaluate(listOf(base,review.copy(active=false)),185200)[0].dueKm)
        assertNull(Tyres.evaluate(listOf(base),null)[0].usedKm)
        assertNull(Tyres.evaluate(listOf(base),174999)[0].usedKm)
        assertNull(Tyres.evaluate(listOf(base.copy(km=Int.MAX_VALUE)),null)[0].dueKm)
    }

    @Test fun metadataChangesDoNotCreateAnotherNotificationCycle() {
        val base=event("mount")
        assertEquals(Tyres.evaluate(listOf(base),180000)[0].generation,
            Tyres.evaluate(listOf(base.copy(notes="Revisar presión")),180000)[0].generation)
        assertNotEquals(Tyres.evaluate(listOf(base),180000)[0].generation,
            Tyres.evaluate(listOf(base.copy(intervalKm=15000)),180000)[0].generation)
    }
}
