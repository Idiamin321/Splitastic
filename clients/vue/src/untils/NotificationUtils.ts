import firebase from "firebase/app";
import "firebase/messaging";
import config from "@/../config";
import { UserApi } from "@/generated/api-axios";
import { StateUtils } from "./StateUtils";

export class NotificationUtils {
  public static init() {
    firebase.initializeApp(config.firebase.config);
    const messaging = firebase.messaging();
    messaging.usePublicVapidKey(config.firebase.publicVapidKey);
    messaging.onMessage((notification) => {
      this.showMessage(notification.notification);
    });

    this.refreshFirebaseToken();
    messaging.onTokenRefresh(() => {
      return this.refreshFirebaseToken();
    });
  }

  public static showMessage(notification: any) {
    if (Notification.permission == "granted") {
      return navigator.serviceWorker.ready.then((registration) => {
        return registration.showNotification(notification.title, notification);
      });
    }
  }

  public static async refreshFirebaseToken() {
    try {
      const messaging = firebase.messaging();
      const token = await messaging.getToken();
      console.log("Token: " + token);
      this.sendTokenToServer(token); //we dont want to have await here
    } catch (err) {
      console.log("Unable to get permission to notify.", err);
    }
  }

  private static async sendTokenToServer(token: string) {
    const api = new UserApi({
      basePath: config.basePath,
      apiKey: StateUtils.getApiKey(),
    });
    await api.userFirebaseTokenPut(token);
  }
}
