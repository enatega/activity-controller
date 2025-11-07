import { requireNativeModule } from "expo";
import type {
  StartLiveActivityFn,
  UpdateLiveActivityFn,
  StopLiveActivityFn,
  IsLiveActivityRunningFn,
} from "./ActivityController.types";

const nativeModule = requireNativeModule("ActivityController");

export const startLiveActivity:StartLiveActivityFn = async (params) => {
  const stringParams = JSON.stringify(params);
  const result = await nativeModule.startLiveActivity(stringParams);
  console.log("Live Activity:", result);
  // result = { activityId: "XXXX-XXXX", pushToken: "abcd1234..." }
  return result;
};


export const updateLiveActivity: UpdateLiveActivityFn = async (params) => {
  const stringParams = JSON.stringify(params);
  if (typeof nativeModule.updateLiveActivity === "function") {
    return nativeModule.updateLiveActivity(stringParams);
  }
  return Promise.resolve();
};


export const stopLiveActivity: StopLiveActivityFn = async () => {
  return nativeModule.stopLiveActivity();
};


export const isLiveActivityRunning: IsLiveActivityRunningFn = () => {
  return nativeModule.isLiveActivityRunning();
};


export const areLiveActivitiesEnabled: boolean =
  nativeModule.areLiveActivitiesEnabled;

  export const saveImageToAppGroup : (imageUri: string) => Promise<string> =
  (imageUri: string) => {
    return nativeModule.saveImageToAppGroup(imageUri);
  };

export const cleanAppGroupImages = (maxAgeHours: number): Promise<void> => {
  return nativeModule.cleanAppGroupImages(maxAgeHours);
};