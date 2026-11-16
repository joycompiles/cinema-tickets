# Cinema Tickets

Java 21+ implementation of the DWP cinema ticket purchase exercise.

Based on [dwp/cinema-tickets](https://github.com/dwp/cinema-tickets).

## Requirements

- JDK 21 or newer
- Apache Maven 3.9+

## Build and test

From the directory that contains `pom.xml`:

```bash
mvn clean test
```

Optional macOS setup if multiple JDKs are installed:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

## Business rules

| Ticket type | Price | Seat |
|-------------|-------|------|
| INFANT      | £0    | No (sits on an adult's lap) |
| CHILD       | £15   | Yes |
| ADULT       | £25   | Yes |

- Up to 25 tickets per purchase, **including infants**
- Child and Infant tickets require at least one Adult

### Assumption

The brief states that infants sit on an adult's lap. This solution therefore rejects purchases where infants outnumber adults.

## Design

- `TicketService` and `thirdparty.*` are unchanged
- `TicketTypeRequest` is an immutable value object
- Constructor injection allows payment and reservation providers to be replaced and mocked in tests
- All purchase validation completes before either provider is called

## Tests

Tests cover pricing, seat allocation, adult requirements, invalid inputs, the 25-ticket boundary (including infants), repeated ticket types, and integer-overflow protection.
