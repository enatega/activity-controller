import Foundation
import ActivityKit
import OSLog
import React


// MARK: - JSON param structs used by the module
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
@objc(ActivityController) // name exposed to JS: NativeModules.ActivityController
class ActivityController: NSObject, RCTBridgeModule {

  // Optional: explicit moduleName (some RN setups expect it)
  @objc
  static func moduleName() -> String! {
    return "ActivityController"
  }

  // Tell RN whether module requires main queue. This module does not.
  @objc
  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "ActivityController", category: "ActivityController")

  // MARK: - areLiveActivitiesEnabled
  @objc
  func areLiveActivitiesEnabled(_ resolve: @escaping RCTPromiseResolveBlock,
                                rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.2, *) {
      resolve(ActivityAuthorizationInfo().areActivitiesEnabled)
    } else {
      resolve(false)
    }
  }

  // MARK: - startLiveActivity
  // JS will call: ActivityController.startLiveActivity(JSON.stringify(params))
  @objc
  func startLiveActivity(_ rawData: String,
                         resolver resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        guard #available(iOS 16.2, *) else {
          reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
          return
        }

        // decode input
        guard let jsonData = rawData.data(using: .utf8) else {
          throw ActivityControllerError.unexpected("Invalid rawData string")
        }

        let params: StartParams
        do {
          params = try JSONDecoder().decode(StartParams.self, from: jsonData)
        } catch {
          throw ActivityControllerError.unexpected("Failed to decode start params: \(error.localizedDescription)")
        }

        // ensure no existing activity (per original logic)
        if !Activity<DeliveryAttributes>.activities.isEmpty {
          reject("ACTIVITY_ALREADY_RUNNING", "A live activity is already running", nil)
          return
        }

        // ensure authorization
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
          reject("ACTIVITY_NOT_AUTHORIZED", "Live activities are not authorized", nil)
          return
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

        // request activity with push token
        let activity = try Activity<DeliveryAttributes>.request(
          attributes: attributes,
          contentState: contentState,
          pushType: .token
        )

        // wait for first push token (same approach as original)
        var tokenString = ""
        for await tokenData in activity.pushTokenUpdates {
          tokenString = tokenData.map { String(format: "%02x", $0) }.joined()
          break
        }

        let result: [String: Any] = [
          "activityId": activity.id,
          "pushToken": tokenString
        ]

        resolve(result)

      } catch let err as ActivityControllerError {
        logger.error("startLiveActivity error: \(err.reason)")
        reject("START_LIVE_ACTIVITY_ERROR", err.reason, err)
      } catch {
        logger.error("startLiveActivity unexpected: \(error.localizedDescription)")
        reject("START_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - updateLiveActivity
  @objc
  func updateLiveActivity(_ rawData: String,
                          resolver resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        guard #available(iOS 16.2, *) else {
          reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
          return
        }

        guard let activity = Activity<DeliveryAttributes>.activities.first else {
          reject("ACTIVITY_NOT_FOUND", "No live activity is running", nil)
          return
        }

        guard let jsonData = rawData.data(using: .utf8) else {
          throw ActivityControllerError.unexpected("Invalid rawData string")
        }

        let params: UpdateParams
        do {
          params = try JSONDecoder().decode(UpdateParams.self, from: jsonData)
        } catch {
          throw ActivityControllerError.unexpected("Failed to decode update params: \(error.localizedDescription)")
        }

        let updatedState = DeliveryAttributes.ContentState(
          orderStatus: params.orderStatus,
          estimatedDelivery: params.estimatedDelivery,
          progress: params.progress
        )

        await activity.update(using: updatedState)
        resolve(nil)
      } catch let err as ActivityControllerError {
        logger.error("updateLiveActivity error: \(err.reason)")
        reject("UPDATE_LIVE_ACTIVITY_ERROR", err.reason, err)
      } catch {
        logger.error("updateLiveActivity unexpected: \(error.localizedDescription)")
        reject("UPDATE_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - stopLiveActivity
  @objc
  func stopLiveActivity(_ resolve: @escaping RCTPromiseResolveBlock,
                        rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        guard #available(iOS 16.2, *) else {
          reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
          return
        }

        guard let activity = Activity<DeliveryAttributes>.activities.first else {
          reject("ACTIVITY_NOT_FOUND", "No live activity is running", nil)
          return
        }

        await activity.end(dismissalPolicy: .immediate)
        resolve(nil)
      } catch {
        logger.error("stopLiveActivity unexpected: \(error.localizedDescription)")
        reject("STOP_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - isLiveActivityRunning
  @objc
  func isLiveActivityRunning(_ resolve: @escaping RCTPromiseResolveBlock,
                             rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.2, *) {
      resolve(!Activity<DeliveryAttributes>.activities.isEmpty)
    } else {
      resolve(false)
    }
  }

  // MARK: - saveImageToAppGroup
  // JS: ActivityController.saveImageToAppGroup(url)
  @objc
  func saveImageToAppGroup(_ imageUrl: String,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        logger.log("saveImageToAppGroup start: \(imageUrl)")

        guard let url = URL(string: imageUrl) else {
          throw ActivityControllerError.unexpected("Invalid image URL: \(imageUrl)")
        }

        let appGroupId = "group.com.enatega.customerapp"
        guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
          throw ActivityControllerError.unexpected("Unable to access App Group container for ID: \(appGroupId)")
        }

        let imagesFolder = containerUrl.appendingPathComponent("WidgetImages", isDirectory: true)
        try? FileManager.default.createDirectory(at: imagesFolder, withIntermediateDirectories: true)

        let fileUrl = imagesFolder.appendingPathComponent(url.lastPathComponent)

        if FileManager.default.fileExists(atPath: fileUrl.path) {
          logger.log("file exists at \(fileUrl.path)")
          resolve(fileUrl.path)
          return
        }

        logger.log("downloading \(url.absoluteString)")
        let (data, response) = try await URLSession.shared.data(from: url)

        guard let httpResponse = response as? HTTPURLResponse else {
          throw ActivityControllerError.unexpected("Invalid response type while downloading image")
        }

        guard httpResponse.statusCode == 200 else {
          throw ActivityControllerError.unexpected("Failed to download image: HTTP \(httpResponse.statusCode)")
        }

        try data.write(to: fileUrl, options: .atomic)
        logger.log("saved image to \(fileUrl.path)")
        resolve(fileUrl.path)
      } catch let err as ActivityControllerError {
        logger.error("saveImageToAppGroup error: \(err.reason)")
        reject("SAVE_IMAGE_ERROR", err.reason, err)
      } catch {
        logger.error("saveImageToAppGroup unexpected: \(error.localizedDescription)")
        reject("SAVE_IMAGE_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - cleanAppGroupImages
  @objc
  func cleanAppGroupImages(_ maxAgeHours: Double,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        let appGroupId = "group.com.enatega.customerapp"
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
          logger.error("Unable to access App Group container.")
          resolve(nil)
          return
        }

        let imagesFolder = containerURL.appendingPathComponent("WidgetImages", isDirectory: true)
        guard FileManager.default.fileExists(atPath: imagesFolder.path) else {
          logger.log("No Images folder — skipping cleanup.")
          resolve(nil)
          return
        }

        let expirySeconds = maxAgeHours * 3600
        let now = Date()
        let fm = FileManager.default

        guard let files = try? fm.contentsOfDirectory(at: imagesFolder, includingPropertiesForKeys: [.contentModificationDateKey], options: [.skipsHiddenFiles]) else {
          logger.error("Could not list contents of Images folder.")
          resolve(nil)
          return
        }

        for file in files {
          let fileName = file.lastPathComponent
          do {
            let attrs = try fm.attributesOfItem(atPath: file.path)
            if let modDate = attrs[.modificationDate] as? Date {
              let ageSeconds = now.timeIntervalSince(modDate)
              if ageSeconds > expirySeconds {
                logger.log("Removing expired image: \(fileName)")
                try? fm.removeItem(at: file)
              } else {
                logger.log("Keeping recent image: \(fileName)")
              }
            } else {
              logger.warning("Could not read modification date for: \(fileName)")
            }
          } catch {
            logger.error("Failed to process file \(fileName): \(error.localizedDescription)")
          }
        }

        logger.log("Image cleanup complete.")
        resolve(nil)
      } catch {
        logger.error("cleanAppGroupImages unexpected: \(error.localizedDescription)")
        reject("CLEAN_IMAGES_ERROR", error.localizedDescription, error)
      }
    }
  }
}
