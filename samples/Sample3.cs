using System;
using System.Collections.Generic;
using System.Linq;

namespace RegionHighlights.Samples
{
    #region Models

    public class User
    {
        public int Id { get; set; }
        public string Name { get; set; }
        public string Email { get; set; }
        public DateTime CreatedAt { get; set; }
    }

    public class Order
    {
        public int Id { get; set; }
        public int UserId { get; set; }
        public List<OrderItem> Items { get; set; } = new();
        public decimal Total => Items.Sum(i => i.Price * i.Quantity);
    }

    public class OrderItem
    {
        public string ProductName { get; set; }
        public decimal Price { get; set; }
        public int Quantity { get; set; }
    }

    #endregion

    #region Services

    public class UserService
    {
        private readonly List<User> _users = new();

        #region CRUD Operations

        public User Create(string name, string email)
        {
            var user = new User
            {
                Id = _users.Count + 1,
                Name = name,
                Email = email,
                CreatedAt = DateTime.UtcNow
            };
            _users.Add(user);
            return user;
        }

        public User GetById(int id) => _users.FirstOrDefault(u => u.Id == id);

        public List<User> GetAll() => _users.ToList();

        public bool Delete(int id)
        {
            var user = GetById(id);
            return user != null && _users.Remove(user);
        }

        #endregion

        #region Validation

        public bool IsValidEmail(string email)
        {
            return !string.IsNullOrWhiteSpace(email) && email.Contains('@');
        }

        public bool IsUnique(string email)
        {
            return _users.All(u => u.Email != email);
        }

        #endregion
    }

    public class OrderService
    {
        private readonly List<Order> _orders = new();

        #region Order Management

        public Order CreateOrder(int userId)
        {
            var order = new Order { Id = _orders.Count + 1, UserId = userId };
            _orders.Add(order);
            return order;
        }

        public void AddItem(Order order, string product, decimal price, int quantity)
        {
            order.Items.Add(new OrderItem
            {
                ProductName = product,
                Price = price,
                Quantity = quantity
            });
        }

        public List<Order> GetOrdersByUser(int userId)
        {
            return _orders.Where(o => o.UserId == userId).ToList();
        }

        #endregion

        #region Reporting

        public decimal GetTotalRevenue() => _orders.Sum(o => o.Total);

        public Dictionary<int, decimal> GetRevenueByUser()
        {
            return _orders
                .GroupBy(o => o.UserId)
                .ToDictionary(g => g.Key, g => g.Sum(o => o.Total));
        }

        #endregion
    }

    #endregion

    #region Program Entry Point

    public static class Program
    {
        public static void Main()
        {
            var userService = new UserService();
            var orderService = new OrderService();

            var alice = userService.Create("Alice", "alice@example.com");
            var bob = userService.Create("Bob", "bob@example.com");

            var order = orderService.CreateOrder(alice.Id);
            orderService.AddItem(order, "Keyboard", 79.99m, 1);
            orderService.AddItem(order, "Mouse", 49.99m, 2);

            Console.WriteLine($"Users: {userService.GetAll().Count}");
            Console.WriteLine($"Order Total: {order.Total:C}");
            Console.WriteLine($"Revenue: {orderService.GetTotalRevenue():C}");
        }
    }

    #endregion
}
