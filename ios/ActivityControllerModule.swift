import Foundation
import ActivityKit
import OSLog

@objc(ActivityController)
class ActivityControllerModule: NSObject {

    // MARK: - Check if Live Activities are enabled
     @objc
    func areLiveActivitiesEnabled(_ resolve: @escaping RCTPromiseResolveBlock,
                                  rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.1, *) {
            if ActivityAuthorizationInfo().areActivitiesEnabled {
                resolve(true)
            } else {
                resolve(false)
            }
        } else {
            // Fallback on earlier versions
        }
    }

    // MARK: - Start Live Activity
    @objc
    func startLiveActivity(_ rawData: String,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
        do {
            let data = try parseStartData(rawData)
            let attributes = DeliveryAttributes(
                orderId: data.orderId,
                itemName: data.itemName,
                totalAmount: data.totalAmount,
                vehicleNumber: data.vehicleNumber,
                itemImageUrl: data.itemImageUrl
            )

            let contentState = DeliveryAttributes.ContentState(
                orderStatus: data.orderStatus,
                estimatedDelivery: data.estimatedDelivery,
                progress: data.progress
            )

            if #available(iOS 16.1, *) {
                let activity = try Activity<DeliveryAttributes>.request(
                    attributes: attributes,
                    contentState: contentState
                )
                resolve(activity.id)
            } else {
                resolve(nil)
            }

        } catch let error as ActivityControllerError {
            reject("START_ERROR", error.reason, error)

        } catch {
            reject("START_ERROR", error.localizedDescription, error)
        }
    }

    // MARK: - Update Live Activity
    @objc
    func updateLiveActivity(_ rawData: String,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.1, *) {
            do {
                let data = try parseUpdateData(rawData)

                let contentState = DeliveryAttributes.ContentState(
                    orderStatus: data.orderStatus,
                    estimatedDelivery: data.estimatedDelivery,
                    progress: data.progress
                )

                let activities = Activity<DeliveryAttributes>.activities

                if let activity = activities.first {
                    Task {
                        await activity.update(using: contentState)
                        resolve(true)
                    }
                } else {
                    reject("UPDATE_ERROR", "No running activity found", nil)
                }

            } catch let error as ActivityControllerError {
                reject("UPDATE_ERROR", error.reason, error)

            } catch {
                reject("UPDATE_ERROR", error.localizedDescription, error)
            }
        } else {
            resolve(nil)
        }
    }

    // MARK: - Stop Live Activity
    @objc
    func stopLiveActivity(_ resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.1, *) {
            let activities = Activity<DeliveryAttributes>.activities
            guard let activity = activities.first else {
                resolve(false)
                return
            }

            Task {
                await activity.end(dismissalPolicy: .immediate)
                resolve(true)
            }
        } else {
            resolve(nil)
        }
    }

    // MARK: - Check if Live Activity Exists
    @objc
    func isLiveActivityRunning(_ resolve: @escaping RCTPromiseResolveBlock,
                               rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.1, *) {
            resolve(!Activity<DeliveryAttributes>.activities.isEmpty)
        } else {
            resolve(false)
        }
    }

    // MARK: - Parse Start Data (JSON)
    private func parseStartData(_ raw: String) throws -> StartData {
        guard let json = raw.data(using: .utf8) else {
            throw ActivityControllerError.unexpected("Invalid raw string")
        }

        do {
            return try JSONDecoder().decode(StartData.self, from: json)
        } catch {
            throw ActivityControllerError.unexpected("Failed to parse start data")
        }
    }

    // MARK: - Parse Update Data (JSON)
    private func parseUpdateData(_ raw: String) throws -> UpdateData {
        guard let json = raw.data(using: .utf8) else {
            throw ActivityControllerError.unexpected("Invalid raw string")
        }

        do {
            return try JSONDecoder().decode(UpdateData.self, from: json)
        } catch {
            throw ActivityControllerError.unexpected("Failed to parse update data")
        }
    }

    // MARK: - Codable Structures for JSON
    private struct StartData: Codable {
        let orderId: String
        let itemName: String
        let totalAmount: String
        let vehicleNumber: String
        let itemImageUrl: String
        let orderStatus: String
        let estimatedDelivery: String
        let progress: Double
    }

    private struct UpdateData: Codable {
        let orderStatus: String
        let estimatedDelivery: String
        let progress: Double
    }
}
