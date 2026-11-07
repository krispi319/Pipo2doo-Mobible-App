package ai.mwm.client.cloud

import org.junit.Assert.*
import org.junit.Test

class StartupPagingTest {
    @Test fun smallAndExactBoundaryResultsDoNotFetchAnEmptyPage() {
        assertTrue(recordPageComplete("0-19/20",0,20))
        assertTrue(recordPageComplete("0-499/500",0,500))
        assertTrue(recordPageComplete("500-500/501",500,1))
    }
    @Test fun serverRowCapBelowRequestedPageSizeDoesNotTruncateTasks() {
        assertFalse(recordPageComplete("0-99/250",0,100))
        assertFalse(recordPageComplete("100-199/250",100,100))
        assertTrue(recordPageComplete("200-249/250",200,50))
    }
    @Test fun missingOrUnknownCountsContinueUntilEmpty() {
        assertFalse(recordPageComplete(null,0,12))
        assertFalse(recordPageComplete("0-11/*",0,12))
        assertTrue(recordPageComplete("*/0",0,0))
    }
}
