package dev.agneswd.stillpoint.plus

import org.junit.Assert.assertEquals
import org.junit.Test

class EntitlementTest {
    private val purchased = StorePurchase(setOf("stillpoint_plus"), "token", PurchaseState.PURCHASED, false)
    private val pending = purchased.copy(state = PurchaseState.PENDING)

    @Test fun pendingDoesNotUnlockOrAcknowledge() {
        assertEquals(EntitlementUpdate(Entitlement.PENDING, emptySet()),
            reduceEntitlement(Entitlement.LOCKED, PurchaseQuery.Success(listOf(pending))))
    }

    @Test fun purchasedUnlocksBeforeAcknowledgementAndRequestsAcknowledgement() {
        assertEquals(EntitlementUpdate(Entitlement.UNLOCKED, setOf("token")),
            reduceEntitlement(Entitlement.LOCKED, PurchaseQuery.Success(listOf(purchased))))
    }

    @Test fun failedQueryNeverChangesAnyEntitlement() {
        Entitlement.entries.forEach {
            assertEquals(EntitlementUpdate(it, emptySet()), reduceEntitlement(it, PurchaseQuery.Failed))
        }
    }

    @Test fun successfulEmptyQueryRevokesCachedPurchaseAfterRefund() {
        assertEquals(EntitlementUpdate(Entitlement.LOCKED, emptySet()),
            reduceEntitlement(Entitlement.UNLOCKED, PurchaseQuery.Success(emptyList())))
    }

    @Test fun duplicatePurchasesAcknowledgeEachTokenOnlyOnce() {
        assertEquals(EntitlementUpdate(Entitlement.UNLOCKED, setOf("token")),
            reduceEntitlement(Entitlement.PENDING, PurchaseQuery.Success(listOf(pending, purchased, purchased))))
    }

    @Test fun acknowledgedPurchaseDoesNotNeedAnotherAcknowledgement() {
        assertEquals(EntitlementUpdate(Entitlement.UNLOCKED, emptySet()),
            reduceEntitlement(Entitlement.LOCKED, PurchaseQuery.Success(listOf(purchased.copy(acknowledged = true)))))
    }

    @Test fun unrelatedAndUnspecifiedPurchasesCannotUnlock() {
        assertEquals(EntitlementUpdate(Entitlement.LOCKED, emptySet()),
            reduceEntitlement(Entitlement.UNLOCKED, PurchaseQuery.Success(listOf(
                purchased.copy(products = setOf("something_else")), purchased.copy(state = PurchaseState.UNSPECIFIED)))))
    }

    @Test fun pendingPurchaseCanCompleteOrDisappearOnTheNextSuccessfulQuery() {
        assertEquals(EntitlementUpdate(Entitlement.UNLOCKED, setOf("token")),
            reduceEntitlement(Entitlement.PENDING, PurchaseQuery.Success(listOf(purchased))))
        assertEquals(EntitlementUpdate(Entitlement.LOCKED, emptySet()),
            reduceEntitlement(Entitlement.PENDING, PurchaseQuery.Success(emptyList())))
    }

    @Test fun purchaseEventCannotRevokeAnExistingEntitlement() {
        assertEquals(EntitlementUpdate(Entitlement.UNLOCKED, emptySet()),
            reduceEntitlement(Entitlement.UNLOCKED, PurchaseQuery.Success(listOf(pending)), authoritative = false))
        assertEquals(EntitlementUpdate(Entitlement.PENDING, emptySet()),
            reduceEntitlement(Entitlement.PENDING, PurchaseQuery.Success(emptyList()), authoritative = false))
    }
}
