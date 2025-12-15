package com.innovatewithomer.juiceforu.repo

import com.innovatewithomer.juiceforu.Database
import com.innovatewithomer.juiceforu.models.Customer

class CustomerRepository {

    fun addCustomer(customer: Customer) {
        val sql = "INSERT INTO customers (name, phone, email, type) VALUES (?, ?, ?, ?)"
        Database.getConnection().use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, customer.name)
                stmt.setString(2, customer.phone)
                stmt.setString(3, customer.email)
                stmt.setString(4, customer.type)
                stmt.executeUpdate()
            }
        }
    }

    fun getAllCustomers(): List<Customer> {
        val customers = mutableListOf<Customer>()
        val sql = "SELECT * FROM customers"
        Database.getConnection().use { conn ->
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    customers.add(
                        Customer(
                            id = rs.getInt("id"),
                            name = rs.getString("name"),
                            phone = rs.getString("phone"),
                            email = rs.getString("email"),
                            type = rs.getString("type")
                        )
                    )
                }
            }
        }
        return customers
    }
}
