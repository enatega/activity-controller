import type { StyleProp, ViewStyle } from 'react-native';

export type OnLoadEventPayload = {
  url: string;
};

export type ActivityControllerModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
};

export type ChangeEventPayload = {
  value: string;
};

export type ActivityControllerViewProps = {
  url: string;
  onLoad: (event: { nativeEvent: OnLoadEventPayload }) => void;
  style?: StyleProp<ViewStyle>;
};

export type SetScore = {
  playerOne: any
  playerTwo: any
}

export type LiveActivityParams = {
  
    orderId: string,
    itemName: string,
    totalAmount: string,
    vehicleNumber: string,
    itemImageUrl: string, // send remote/local URL

    // initial content state
    orderStatus: string,
    estimatedDelivery: string,
    progress: number,
       
}

export type StartLiveActivityFn = (
  params: LiveActivityParams
) => Promise<{ activityId: string, pushToken:  string }>

export type UpdateLiveActivityFn = (
  params: { setScores: SetScore[] }
) => Promise<void>

export type StopLiveActivityFn = () => Promise<void>
export type IsLiveActivityRunningFn = () => boolean