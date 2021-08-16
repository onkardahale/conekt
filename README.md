# Conekt

A free-to-use, Java client for communicating with Kite Connect API.\
Conekt simulates a browser login to work **without** an **API key**.

---
## Usage
- [Download Conekt 1.0 jar file](https://github.com/onkardahale/conekt/blob/master/dist/conekt-1.0.jar)
- To use Conekt in **Android**, you need to include jar file in the libs directory and add the following line in you module's gradle file ``` compile files('libs/conekt-1.0.jar') ```

---
## API Usage

```java
// Constructors are overloaded
// Initializes Connect with no proxy and logging disabled.
Conekt conekt = new Conekt();

// To enable logging and to pass proxy while initialization
Conekt conekt = new Conekt(proxy, true);

// Pass creds for login
conekt.setCred("your_userId", "your_password", "your_twoFactorPIN");

// Call methods like the official Java Client
```
---
## Documentation
Conekt was built keeping the official client in mind, so the usage is same as the official client.

- [Official Java Library documentation](https://kite.trade/docs/javakiteconnect/v3/)
- [Kite Connect HTTP API documentation](https://kite.trade/docs/connect/v3/)

Models returned for methods like conket.getProfile(), etc are the same as official client.\
Be sure to import the models with ```com.zerodhatech.models.*``` in order to use them.

---
## License
Conekt is licensed under MIT License.\
Copyright © 2021 [Onkar Dahale](https://github.com/onkardahale)
