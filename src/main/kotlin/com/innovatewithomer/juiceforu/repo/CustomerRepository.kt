package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.Customer
import java.sql.Connection

class CustomerRepository {

    fun addCustomer(customer: Customer, conn: Connection? = null) {
        val sql = "INSERT INTO customers (name, phone, email, type) VALUES (?, ?, ?, ?)"
        val execute = { c: Connection ->
            c.prepareStatement(sql).use { stmt ->
                stmt.setString(1, customer.name)
                stmt.setString(2, customer.phone)
                stmt.setString(3, customer.email)
                stmt.setString(4, customer.type)
                stmt.executeUpdate()
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
    }

    fun getAllCustomers(conn: Connection? = null): List<Customer> {
        val customers = mutableListOf<Customer>()
        val sql = "SELECT * FROM customers ORDER BY name ASC"
        val execute = { c: Connection ->
            c.createStatement().use { stmt ->
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        customers.add(
                            Customer(
                                id = rs.getInt("id"),
                                name = rs.getString("name") ?: "",
                                phone = rs.getString("phone"),
                                email = rs.getString("email"),
                                type = rs.getString("type") ?: "Regular"
                            )
                        )
                    }
                }
            }
        }
        if (conn != null) {
            execute(conn)
        } else {
            Database.getConnection().use { execute(it) }
        }
        return customers
    }
}

