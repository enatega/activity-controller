import { TurboModule, TurboModuleRegistry } from "react-native";

export interface Spec extends TurboModule {
  readonly areLiveActivitiesEnabled: boolean;

  startLiveActivity(rawData: string): Promise<{ activityId: string; pushToken: string }>;

  updateLiveActivity(rawData: string): Promise<void>;

  stopLiveActivity(): Promise<void>;

  isLiveActivityRunning(): boolean;

  saveImageToAppGroup(imageUrl: string): Promise<string>;

  cleanAppGroupImages(maxAgeHours: number): Promise<void>;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  "ActivityController"
);