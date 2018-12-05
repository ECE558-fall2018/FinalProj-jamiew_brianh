/**
 *  Jamie Williams - Updated 12/1/2018
 *	Security App - ECE 558 Final Project, Fall 2018
 *
 * Functions to alert a user if base station detects an issue or stops sending timestamps
 * 		-Node.js code to create functions in Firebase to track Real Time Database values
 *		-Alerts the user using a Firebase Cloud Messaging notification to phone
 */

"use strict"; // JavaScript convention to require safer coding standards
 
// The Cloud Functions for Firebase SDK to create Cloud Functions and setup triggers.
const functions = require('firebase-functions');

// The Firebase Admin SDK to access the Firebase Realtime Database.
const admin = require('firebase-admin');
admin.initializeApp();


// Check if Pi has been connected/disconnected
exports.checkBaseStationConnected = functions.database.ref('users/{userUid}/pi_connected')
	.onWrite(async (change, context) => {
	const userUid = context.params.userUid;
	//var piOnlineRef = firebase.database().ref('/users/${userUid}/pi_connected');
	const prevState = change.before.val();
	const currentState = change.after.val();
	//console.log('Pi connection change noted for UID: ', userUid, '. Prev State: ', prevState , ', Current State: ', currentState);
	
	// Check that pi_connected changed from true to false
	if(prevState && !currentState) {
		console.log('Pi disconnected for user: ', userUid, '. Prev State: ', prevState , ', Current State: ', currentState);
		// Send notification to user on disconnect
		
		// Retrieve notification token - unique to device to send to
		const tokenRef = admin.database().ref(`/users/${userUid}/apptoken`);
		var token;
		const snapshot2 = await tokenRef.once('value', (snap) => {
			token = snap.val();
		});
		
		// Build Notification to send to phone app
		// Notification details.
		const payload = {
			notification: {
				title: 'Security Base Station has lost connection!',
				body: `Security Base Station has disconnected from the server`,
			}
		};

		// Send notification to user of base station alarm
		admin.messaging().sendToDevice(token, payload)
			.then((response) => {
				// Check for error with message sending
				const error = response.results.error;
				if (error) {
					console.error('Could not send notification to ', userUid, '. With error: ', error);
				} else {
					console.log('Sucessfully sent message to ', userUid, '. Response of: ', response);
				}
				return response;
			})
			.catch((error) => {
				// Catch error while sending
				console.error('Failure sending notification to ', userUid, '. With error: ', error);
			});
		
		
		
		
		
		return null;
	} 
	else {
		console.log('Pi connected for user: ', userUid, '. Prev State: ', prevState , ', Current State: ', currentState);
	} 
	});


// Check for alarm from Pi and send notification (if armed)
exports.checkStationAlarm = functions.database.ref('/users/{userUid}/pi_triggered')
	.onWrite(async (change, context) => {
	// Create get user ID of the user whose system triggered
	const userUid = context.params.userUid;
	
	// Get the value of alarm_armed boolean
	const armedRef = admin.database().ref(`/users/${userUid}/pi_armed`);
	var armedVal;
	const snapshot = await armedRef.once('value', (snap) => {
		armedVal = snap.val();
	});
	const armed = armedVal;
	// Get the value of pi_triggered boolean
	const alarmed = change.after.val();
	const triggerRef = admin.database().ref(`/users/${userUid}/pi_triggered`);
	
	// Check if there is an alarm, and if the alarm is armed
	if (!alarmed || !armed) {
		// No alarm, or not armed, console write to log the change
		console.log('Noted write to pi_triggered, no alarm activated for user: ', userUid, '. Armed is: ', armed , ', Alarmed is', alarmed);
		if(alarmed && !armed) {
		// If trigger when not alarmed, turn off trigger
		triggerRef.set(false);	
		}		
		return null;
	}
	// Alarm is triggered while armed, write status to console
	console.log('Noted activation pi_triggered, for user: ', userUid, '. Armed is: ', armed , ', Alarmed is', alarmed);
	
	// Reset pi_triggered value and armed value
	triggerRef.set(false);
	armedRef.set(false);
	
	// Retrieve notification token - unique to device to send to
	const tokenRef = admin.database().ref(`/users/${userUid}/apptoken`);
	var token;
	const snapshot2 = await tokenRef.once('value', (snap) => {
		token = snap.val();
	});
	
	// Build Notification to send to phone app
	// Notification details.
	const payload = {
		notification: {
			title: 'Alarm recieved from Security Base Station!',
			body: `Security Base Station sent an alarm - Uknown reason`,
		}
	};

	// Send notification to user of base station alarm
	admin.messaging().sendToDevice(token, payload)
		.then((response) => {
			// Check for error with message sending
			const error = response.results.error;
			if (error) {
				console.error('Could not send notification to ', userUid, '. With error: ', error);
			} else {
				console.log('Sucessfully sent message to ', userUid, '. Response of: ', response);
			}
			return response;
		})
		.catch((error) => {
			// Catch error while sending
			console.error('Failure sending notification to ', userUid, '. With error: ', error);
		});
	return null;
});