import Foundation
import ActivityKit
import React

// MARK: - Exceptions
final class ActivityUnavailableException: GenericException<Void> {
    override var reason: String { "Live activities are not available on this system." }
}

final class ActivityDataException: GenericException<String> {
    override var reason: String { "Failed to parse Live Activity data: \(param)" }
}

// MARK: - Params
fileprivate struct StartParams: Decodable {
    let orderStatus: String
    let estimatedDelivery: String
    let progress: Double
    let orderId: String
    let itemName: String
    let totalAmount: String
    let vehicleNumber: String
    let itemImageUrl: String
}

fileprivate struct UpdateParams: Decodable {
    let orderStatus: String
    let estimatedDelivery: String
    let progress: Double
}

// MARK: - React Native Bridge
@objc(ActivityController) // <- THIS MAKES IT AVAILABLE IN NativeModules.ActivityController
class ActivityControllerModuleBridge: NSObject {

    // MARK: - Live Activities
    @objc
    func areLiveActivitiesEnabled(_ resolve: @escaping RCTPromiseResolveBlock,
                                  rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.2, *) {
            resolve(ActivityAuthorizationInfo().areActivitiesEnabled)
        } else {
            resolve(false)
        }
    }

    @objc
    func startLiveActivity(_ rawData: String,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task {
            do {
                guard #available(iOS 16.2, *) else {
                    throw ActivityUnavailableException(())
                }

                let data = Data(rawData.utf8)
                let params = try JSONDecoder().decode(StartParams.self, from: data)

                guard Activity<DeliveryAttributes>.activities.isEmpty else {
                    throw ActivityUnavailableException(())
                }

                let attributes = DeliveryAttributes(
                    orderId: params.orderId,
                    itemName: params.itemName,
                    totalAmount: params.totalAmount,
                    vehicleNumber: params.vehicleNumber,
                    itemImageUrl: params.itemImageUrl
                )

                let contentState = DeliveryAttributes.ContentState(
                    orderStatus: params.orderStatus,
                    estimatedDelivery: params.estimatedDelivery,
                    progress: params.progress
                )

                let activity = try Activity<DeliveryAttributes>.request(
                    attributes: attributes,
                    contentState: contentState,
                    pushType: .token
                )

                var tokenString = ""
                for await data in activity.pushTokenUpdates {
                    tokenString = data.map { String(format: "%02x", $0) }.joined()
                    break
                }

                resolve([
                    "activityId": activity.id,
                    "pushToken": tokenString
                ])
            } catch {
                reject("START_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func updateLiveActivity(_ rawData: String,
                            resolver resolve: @escaping RCTPromiseResolveBlock,
                            rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task {
            do {
                guard #available(iOS 16.2, *) else {
                    throw ActivityUnavailableException(())
                }
                guard let activity = Activity<DeliveryAttributes>.activities.first else {
                    throw ActivityUnavailableException(())
                }

                let data = Data(rawData.utf8)
                let params = try JSONDecoder().decode(UpdateParams.self, from: data)

                let updatedState = DeliveryAttributes.ContentState(
                    orderStatus: params.orderStatus,
                    estimatedDelivery: params.estimatedDelivery,
                    progress: params.progress
                )

                await activity.update(using: updatedState)
                resolve(nil)
            } catch {
                reject("UPDATE_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func stopLiveActivity(_ resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task {
            do {
                guard #available(iOS 16.2, *) else {
                    throw ActivityUnavailableException(())
                }
                guard let activity = Activity<DeliveryAttributes>.activities.first else {
                    throw ActivityUnavailableException(())
                }

                await activity.end(dismissalPolicy: .immediate)
                resolve(nil)
            } catch {
                reject("STOP_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func isLiveActivityRunning(_ resolve: @escaping RCTPromiseResolveBlock,
                               rejecter reject: @escaping RCTPromiseRejectBlock) {
        if #available(iOS 16.2, *) {
            resolve(!Activity<DeliveryAttributes>.activities.isEmpty)
        } else {
            resolve(false)
        }
    }

    // MARK: - App Group Image Helpers
    @objc
    func saveImageToAppGroup(_ imageUrl: String,
                             resolver resolve: @escaping RCTPromiseResolveBlock,
                             rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task {
            do {
                guard let url = URL(string: imageUrl) else {
                    throw GenericException("Invalid image URL")
                }

                let appGroupId = "group.com.enatega.customerapp"
                guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
                    throw GenericException("Unable to access App Group container")
                }

                let imagesFolder = containerUrl.appendingPathComponent("WidgetImages", isDirectory: true)
                try? FileManager.default.createDirectory(at: imagesFolder, withIntermediateDirectories: true)

                let fileUrl = imagesFolder.appendingPathComponent(url.lastPathComponent)

                if FileManager.default.fileExists(atPath: fileUrl.path) {
                    resolve(fileUrl.path)
                    return
                }

                let (data, response) = try await URLSession.shared.data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                    throw GenericException("Failed to download image")
                }

                try data.write(to: fileUrl, options: .atomic)
                resolve(fileUrl.path)
            } catch {
                reject("SAVE_IMAGE_ERROR", error.localizedDescription, error)
            }
        }
    }

    @objc
    func cleanAppGroupImages(_ maxAgeHours: Double,
                             resolver resolve: @escaping RCTPromiseResolveBlock,
                             rejecter reject: @escaping RCTPromiseRejectBlock) {
        Task {
            let appGroupId = "group.com.enatega.customerapp"
            guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else { return }

            let imagesFolder = containerURL.appendingPathComponent("WidgetImages", isDirectory: true)
            guard FileManager.default.fileExists(atPath: imagesFolder.path) else { return }

            let expirySeconds = maxAgeHours * 3600
            let now = Date()
            let fm = FileManager.default

            guard let files = try? fm.contentsOfDirectory(at: imagesFolder, includingPropertiesForKeys: [.contentModificationDateKey], options: [.skipsHiddenFiles]) else { return }

            for file in files {
                if let attrs = try? fm.attributesOfItem(atPath: file.path),
                   let modDate = attrs[.modificationDate] as? Date,
                   now.timeIntervalSince(modDate) > expirySeconds {
                    try? fm.removeItem(at: file)
                }
            }
            resolve(nil)
        }
    }
}
