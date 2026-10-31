package ai.mwm.client.cloud

import ai.mwm.client.data.*
import org.junit.Assert.*
import org.junit.Test

class MemberPermissionsTest {
    private val members = listOf(
        PartnerConnection(spaceId="space",uid="a",partnerUid="b",role="user_1",online=true,authenticated=true),
        PartnerConnection(spaceId="space",uid="b",partnerUid="a",role="user_2",online=true,authenticated=true)
    )
    private val list = SharedList(id="list",name="Home",owner=Owner.You,assigneeUid="b")
    private val parent = Task(id="parent",listId=list.id,owner=Owner.You,assigneeUid="b")
    private val child = Task(id="child",listId=list.id,parentId=parent.id,owner=Owner.Me,assigneeUid="a")
    private val state = PipoState(tasks=listOf(parent,child),lists=listOf(list))

    @Test fun bothMembersCanEditEveryAssignmentAndAncestor() {
        for(c in members) for(uid in listOf("a","b","")) {
            assertTrue(canEditList(list.copy(assigneeUid=uid),c))
            assertTrue(canEditTask(child.copy(assigneeUid=uid),state,c))
        }
    }

    @Test fun bothMembersCanEditCompleteReopenRescheduleMoveAndReassign() {
        val destination=list.copy(id="destination",assigneeUid="a")
        for(c in members) {
            val old=state.copy(lists=state.lists+destination)
            val edits=listOf(
                child.copy(title="Edited"), child.copy(status=TaskStatus.Done,completedAt=123),
                child.copy(day=child.day+1), child.copy(listId=destination.id,parentId=""),
                child.copy(owner=Owner.You), child.copy(owner=Owner.Together)
            )
            for(edit in edits) requireSharedPermissions(old,old.copy(tasks=listOf(parent,edit)),c)
            val done=old.copy(tasks=listOf(parent,child.copy(status=TaskStatus.Done)))
            requireSharedPermissions(done,old,c)
            requireSharedPermissions(old,old.copy(lists=listOf(list.copy(name="Renamed",pinned=true,owner=Owner.Me),destination)),c)
        }
    }

    @Test fun bothMembersCanCreateUnderPartnersParentAndDeleteTasksAndLists() {
        for(c in members) {
            requireSharedPermissions(state,state.copy(tasks=state.tasks+child.copy(id="new",owner=Owner.You)),c)
            requireSharedPermissions(state,state.copy(tasks=emptyList(),lists=emptyList()),c)
        }
    }

    @Test fun connectionGuardsRemainInPlace() {
        val c=members.first()
        for(blocked in listOf(c.copy(online=false),c.copy(busy=true),c.copy(uid=""),c.copy(role=""))) {
            assertFalse(canEditList(list,blocked))
            assertFalse(canEditTask(child,state,blocked))
        }
        assertTrue(canEditTask(child,state,PartnerConnection()))
        assertTrue(canEditList(list,PartnerConnection()))
        assertFalse(canEditTask(child,state,PartnerConnection(uid="a")))
        assertFalse(canEditList(list,PartnerConnection(uid="a")))
    }

    @Test fun invalidParentChainsStillFail() {
        val cycle=state.copy(tasks=listOf(parent.copy(parentId=child.id),child))
        val missing=child.copy(parentId="missing")
        assertFalse(canEditTask(child,cycle,members.first()))
        assertFalse(canEditTask(missing,state,members.first()))
        assertTrue(runCatching {requireSharedPermissions(state,state.copy(tasks=listOf(parent,missing)),members.first())}.isFailure)
    }

    @Test fun partnerNotesRemainSenderOnly() {
        val note=Appreciation(id="note",kind="Thanks",message="Thank you",owner=Owner.You)
        val old=state.copy(notes=listOf(note))
        assertTrue(runCatching {requireSharedPermissions(old,state,members.first())}.isFailure)
        val own=old.copy(notes=listOf(note.copy(owner=Owner.Me)))
        requireSharedPermissions(own,state,members.first())
    }

    @Test fun assigningToAbsentPartnerStillFails() {
        val c=members.first().copy(partnerUid="")
        val old=PipoState()
        val next=old.copy(tasks=listOf(Task(owner=Owner.You)))
        assertTrue(runCatching {requireSharedPermissions(old,next,c)}.isFailure)
    }
}
