import * as React from 'react';

import { ActivityControllerViewProps } from './ActivityController.types';

export default function ActivityControllerView(props: ActivityControllerViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
