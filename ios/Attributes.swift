import ActivityKit

public struct DeliveryAttributes: ActivityAttributes {
    
    public struct ContentState: Codable, Hashable {
        public var orderStatus: String
        public var estimatedDelivery: String
        public var progress: Double  
        
        public init(orderStatus: String, estimatedDelivery: String, progress: Double) {
            self.orderStatus = orderStatus
            self.estimatedDelivery = estimatedDelivery
            self.progress = progress
        }
    }

    public let orderId: String
    public let itemName: String
    public let totalAmount: String
    public let vehicleNumber: String
    public let itemImageUrl: String
}
