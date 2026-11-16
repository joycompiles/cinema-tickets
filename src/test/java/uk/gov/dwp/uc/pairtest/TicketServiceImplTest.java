package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TicketSeyessrviceImplTest {

    @Mock
    private TicketPaymentService paymentService;

    @Mock
    private SeatReservationService reservationService;

    private TicketServiceImpl ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(paymentService, reservationService);
    }

    @Test
    void oneAdult_pays25AndReservesOneSeat() {
        ticketService.purchaseTickets(1L, ticket(Type.ADULT, 1));

        verify(paymentService).makePayment(1L, 25);
        verify(reservationService).reserveSeat(1L, 1);
    }

    @Test
    void twoAdultsAndThreeChildren_pays95AndReservesFiveSeats() {
        ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 2),
                ticket(Type.CHILD, 3)
        );

        verify(paymentService).makePayment(1L, 95);
        verify(reservationService).reserveSeat(1L, 5);
    }

    @Test
    void infantsDoNotPayAndDoNotGetASeat() {
        ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 2),
                ticket(Type.CHILD, 1),
                ticket(Type.INFANT, 2)
        );

        verify(paymentService).makePayment(1L, 65);
        verify(reservationService).reserveSeat(1L, 3);
    }

    @Test
    void sameTicketTypeCanAppearMoreThanOnce() {
        ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 1),
                ticket(Type.ADULT, 2),
                ticket(Type.CHILD, 1)
        );

        verify(paymentService).makePayment(1L, 90);
        verify(reservationService).reserveSeat(1L, 4);
    }

    @Test
    void twentyFiveTicketsIncludingInfantsIsAllowed() {
        ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 10),
                ticket(Type.CHILD, 10),
                ticket(Type.INFANT, 5)
        );

        verify(paymentService).makePayment(1L, 400);
        verify(reservationService).reserveSeat(1L, 20);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, -5L})
    void rejectsInvalidAccountId(Long accountId) {
        assertRejected(() -> ticketService.purchaseTickets(accountId, ticket(Type.ADULT, 1)));
    }

    @Test
    void rejectsMissingTicketRequests() {
        assertRejected(() -> ticketService.purchaseTickets(1L));
    }

    @Test
    void rejectsNullTicketRequestsArray() {
        assertRejected(() -> ticketService.purchaseTickets(1L, (TicketTypeRequest[]) null));
    }

    @Test
    void rejectsNullElementInRequestArray() {
        assertRejected(() -> ticketService.purchaseTickets(1L, ticket(Type.ADULT, 1), null));
    }

    @Test
    void rejectsNullTicketType() {
        assertRejected(() -> ticketService.purchaseTickets(1L, new TicketTypeRequest(null, 1)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsZeroOrNegativeTicketCounts(int quantity) {
        assertRejected(() -> ticketService.purchaseTickets(1L, ticket(Type.ADULT, quantity)));
    }

    @Test
    void rejectsMoreThanTwentyFiveTickets() {
        assertRejected(() -> ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 20),
                ticket(Type.CHILD, 6)
        ));
    }

    @Test
    void rejectsTwentySixTicketsIncludingInfants() {
        assertRejected(() -> ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 10),
                ticket(Type.CHILD, 10),
                ticket(Type.INFANT, 6)
        ));
    }

    @ParameterizedTest
    @MethodSource("overflowQuantities")
    void rejectsHugeTicketCountsWithoutOverflowing(TicketTypeRequest... requests) {
        assertRejected(() -> ticketService.purchaseTickets(1L, requests));
    }

    @Test
    void rejectsChildOrInfantWithoutAnAdult() {
        assertRejected(() -> ticketService.purchaseTickets(1L, ticket(Type.CHILD, 1)));
        assertRejected(() -> ticketService.purchaseTickets(1L, ticket(Type.INFANT, 1)));
        assertRejected(() -> ticketService.purchaseTickets(
                1L,
                ticket(Type.CHILD, 1),
                ticket(Type.INFANT, 1)
        ));
    }

    @Test
    void rejectsMoreInfantsThanAdults() {
        assertRejected(() -> ticketService.purchaseTickets(
                1L,
                ticket(Type.ADULT, 1),
                ticket(Type.INFANT, 2)
        ));
    }

    static Stream<Arguments> overflowQuantities() {
        return Stream.of(
                Arguments.of((Object) new TicketTypeRequest[]{
                        ticket(Type.ADULT, 1),
                        ticket(Type.CHILD, Integer.MAX_VALUE)
                }),
                Arguments.of((Object) new TicketTypeRequest[]{
                        ticket(Type.ADULT, Integer.MAX_VALUE)
                }),
                Arguments.of((Object) new TicketTypeRequest[]{
                        ticket(Type.ADULT, 20),
                        ticket(Type.ADULT, Integer.MAX_VALUE)
                }),
                Arguments.of((Object) new TicketTypeRequest[]{
                        ticket(Type.CHILD, Integer.MAX_VALUE),
                        ticket(Type.ADULT, 1)
                })
        );
    }

    private void assertRejected(Runnable purchase) {
        assertThrows(InvalidPurchaseException.class, purchase::run);
        verifyNoInteractions(paymentService, reservationService);
    }

    private static TicketTypeRequest ticket(Type type, int count) {
        return new TicketTypeRequest(type, count);
    }
}
