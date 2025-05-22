# Simple ERP System

A reactive ERP system built with Java, Spring Boot, Spring WebFlux, and R2DBC to manage core business operations like products, customers, sales, installments, maintenance, and suppliers.

## Prerequisites

- Java Development Kit (JDK) 17 or newer
- Apache Maven 3.6.x or newer

## How to Build

You can build the project using Maven. Navigate to the project's root directory and run:

```bash
mvn clean install
```
This will compile the project and create an executable JAR file in the `target/` directory.

## How to Run

### Using Maven

To run the application directly using the Maven Spring Boot plugin:

```bash
mvn spring-boot:run
```

### Running the JAR

After building the project, you can run the executable JAR:

```bash
java -jar target/simple-erp-system-1.0.0-SNAPSHOT.jar 
```
The application will start, and by default, it will be accessible at `http://localhost:8080`. 
Note: The JAR filename might vary based on the version in `pom.xml`. The example uses `1.0.0-SNAPSHOT` as defined in a previous step.

## Database

The system uses an H2 in-memory database by default. The database schema is automatically created and initialized from the `src/main/resources/schema.sql` file upon startup. Connection details can be found in `src/main/resources/application.properties`.

## Available API Endpoints

The following base paths expose the main resources of the ERP system:

- **Products**: `/api/v1/products`
- **Customers**: `/api/v1/customers`
- **Sales Invoices**: `/api/v1/sales-invoices`
- **Installment Plans & Payments**:
    - `/api/v1/installment-plans` (POST to create)
    - `/api/v1/sales-invoices/{invoiceId}/installment-plan` (GET plan by invoice ID)
    - `/api/v1/installment-plans/{planId}` (GET plan by plan ID)
    - `/api/v1/installment-plans/{planId}/payments` (GET payments for a plan)
    - `/api/v1/installment-payments/{paymentId}/pay` (POST to record a payment)
- **Maintenance Service Records**: `/api/v1/maintenance/service-records`
    - Filtering examples: `/customer/{customerId}`, `/product/{productId}`, `/date-range?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`
- **Suppliers**: `/api/v1/suppliers`

Refer to the respective controller classes for detailed endpoint mappings (e.g., GET by ID, PUT, DELETE).

## Deployment Notes

The application is packaged as a standard Spring Boot executable JAR, which includes an embedded web server (Netty by default for WebFlux). This JAR can be deployed in any environment that has a compatible Java Runtime Environment (JRE).
For production deployments, consider externalizing configuration, setting up proper logging, and potentially containerizing the application using Docker.
