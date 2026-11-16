package uk.gov.dwp.uc.pairtest;

import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Objects;

public class TicketServiceImpl implements TicketService {

    private static final int MAX_TICKETS = 25;
    private static final int ADULT_PRICE = 25;
    private static final int CHILD_PRICE = 15;

    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;

    public TicketServiceImpl(TicketPaymentService paymentService,
                             SeatReservationService reservationService) {
        this.paymentService = Objects.requireNonNull(paymentService, "paymentService");
        this.reservationService = Objects.requireNonNull(reservationService, "reservationService");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {

        validateAccount(accountId);
        TicketCounts counts = countTickets(ticketTypeRequests);
        validatePurchase(counts);

        int amountToPay = calculatePayment(counts);
        int seatsToReserve = calculateSeats(counts);

        paymentService.makePayment(accountId, amountToPay);
        reservationService.reserveSeat(accountId, seatsToReserve);
    }

    private void validateAccount(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException("Account id must be greater than zero");
        }
    }

    private TicketCounts countTickets(TicketTypeRequest... ticketTypeRequests) {
        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException("At least one ticket must be requested");
        }

        int adults = 0;
        int children = 0;
        int infants = 0;
        int total = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                throw new InvalidPurchaseException("Ticket request cannot be null");
            }
            if (request.getTicketType() == null) {
                throw new InvalidPurchaseException("Ticket type is required");
            }

            int quantity = request.getNoOfTickets();
            if (quantity <= 0) {
                throw new InvalidPurchaseException("Number of tickets must be greater than zero");
            }
            if (quantity > MAX_TICKETS - total) {
                throw new InvalidPurchaseException("Cannot purchase more than " + MAX_TICKETS + " tickets at a time");
            }

            switch (request.getTicketType()) {
                case ADULT -> adults += quantity;
                case CHILD -> children += quantity;
                case INFANT -> infants += quantity;
            }
            total += quantity;
        }

        return new TicketCounts(adults, children, infants);
    }

    private void validatePurchase(TicketCounts counts) {
        if (counts.adults == 0) {
            throw new InvalidPurchaseException("Child and infant tickets require at least one adult ticket");
        }
        if (counts.infants > counts.adults) {
            throw new InvalidPurchaseException("Cannot have more infants than adults");
        }
    }

    private int calculatePayment(TicketCounts counts) {
        return (counts.adults * ADULT_PRICE) + (counts.children * CHILD_PRICE);
    }

    private int calculateSeats(TicketCounts counts) {
        return counts.adults + counts.children;
    }

    private static final class TicketCounts {
        private final int adults;
        private final int children;
        private final int infants;

        private TicketCounts(int adults, int children, int infants) {
            this.adults = adults;
            this.children = children;
            this.infants = infants;
        }
    }
}
