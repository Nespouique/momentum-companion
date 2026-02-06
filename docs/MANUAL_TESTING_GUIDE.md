# Guide de test manuel - Momentum Companion

## 1. Prerequis

### Installer Android Studio
1. Telecharger Android Studio (Ladybug 2024.2+ ou plus recent) depuis https://developer.android.com/studio
2. Lancer l'installation et accepter les licences SDK
3. Au premier lancement, choisir "Standard" setup
4. Laisser telecharger les composants SDK (Android 14/API 34 minimum)

### Appareil de test
- **Option A (recommandee)** : Telephone Android 14+ (API 34+) avec Samsung Health ou Google Fit installe
  - Health Connect est integre nativement depuis Android 14
- **Option B** : Emulateur Android Studio
  - Creer un AVD avec API 34+ (Pixel 7 par ex.)
  - **Attention** : Health Connect sur emulateur a des donnees limitees
  - Installer Health Connect depuis le Play Store si pas pre-installe

### Serveur Momentum
- L'instance Momentum doit etre accessible depuis le telephone/emulateur
- Pour tester localement : `npm run dev` dans le repo momentum (port 3001 par defaut)
- Si le telephone est sur le meme reseau : utiliser l'IP locale (ex: `https://192.168.1.X:3001`)
- Pour un emulateur : `https://10.0.2.2:3001` (alias localhost Android)
- Un compte utilisateur doit exister sur Momentum (email + mot de passe)

---

## 2. Build et installation

### Ouvrir le projet
1. Lancer Android Studio
2. **File > Open** > naviguer vers `D:\Elliot\Documents\git\momentum-companion`
3. Attendre la fin du sync Gradle (barre de progression en bas)
   - Si erreur KSP : verifier que le JDK 17 est configure dans **File > Settings > Build > Gradle > Gradle JDK**
   - Si erreur de version : Android Studio peut proposer de mettre a jour les versions dans `libs.versions.toml`

### Generer le Gradle Wrapper
Le projet n'a pas de `gradlew.bat`. Android Studio le genere automatiquement au premier sync.
Si besoin de le generer manuellement :
```bash
# Depuis le dossier du projet, avec Gradle installe
gradle wrapper --gradle-version 8.13
```

### Builder
- **Toolbar > Run (triangle vert)** ou `Shift+F10`
- Ou en ligne de commande : `./gradlew assembleDebug`
- L'APK sera dans `app/build/outputs/apk/debug/app-debug.apk`

### Installer
- Via Android Studio : selectionner l'appareil et cliquer Run
- Via ADB : `adb install app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Parcours de test

### Test 1 : Ecran de configuration (Story 1.1)

**Objectif** : Verifier la connexion au serveur Momentum

| # | Action | Resultat attendu |
|---|--------|-----------------|
| 1.1 | Lancer l'app | L'ecran Setup s'affiche avec les champs URL, email, mot de passe |
| 1.2 | Remplir l'URL du serveur (ex: `https://192.168.1.X:3001`) | Le champ accepte l'URL |
| 1.3 | Cocher "Accepter les certificats self-signed" si HTTPS local | La checkbox se coche |
| 1.4 | Remplir email et mot de passe d'un compte Momentum existant | Les champs se remplissent |
| 1.5 | Cliquer "TESTER LA CONNEXION" | Un spinner s'affiche, puis snackbar "Connexion reussie !" |
| 1.6 | Tester avec un mauvais mot de passe | Snackbar d'erreur affichee |
| 1.7 | Apres connexion reussie, le bouton "SUIVANT" devient actif | Cliquer navigue vers les permissions |

### Test 2 : Permissions Health Connect (Story 1.2)

**Objectif** : Verifier la demande de permissions sante

| # | Action | Resultat attendu |
|---|--------|-----------------|
| 2.1 | L'ecran permissions s'affiche | Liste des 4 permissions (Pas, Calories, Exercice, Sommeil) |
| 2.2 | Cliquer "AUTORISER L'ACCES SANTE" | Le dialog Health Connect s'ouvre |
| 2.3 | Accepter toutes les permissions | Navigation automatique vers le Dashboard |
| 2.4 | Si HC non disponible | Message d'erreur + bouton "Continuer sans Health Connect" |
| 2.5 | Cliquer "Passer pour le moment" | Navigation vers le Dashboard (sans donnees sante) |

### Test 3 : Dashboard (Story 1.4)

**Objectif** : Verifier l'affichage des donnees et la sync manuelle

| # | Action | Resultat attendu |
|---|--------|-----------------|
| 3.1 | Le dashboard s'affiche | Indicateur de connexion vert, URL du serveur, "Dernier sync : Jamais" |
| 3.2 | Les metriques du jour s'affichent | Barres de progression pour Pas, Minutes actives, Calories |
| 3.3 | Si Health Connect autorise | Les donnees du jour (pas, calories, activites) s'affichent |
| 3.4 | Tirer vers le bas (pull-to-refresh) | Les donnees se rafraichissent |
| 3.5 | Cliquer "SYNCHRONISER MAINTENANT" | Spinner, puis les donnees sont envoyees au serveur |
| 3.6 | Verifier cote Momentum | Les donnees apparaissent dans le dashboard web Momentum |
| 3.7 | Activites du jour | Liste des activites avec heure, type, duree |

### Test 4 : Parametres (Story 1.5)

**Objectif** : Verifier les reglages et l'import initial

| # | Action | Resultat attendu |
|---|--------|-----------------|
| 4.1 | Cliquer l'icone engrenage | Navigation vers l'ecran Parametres |
| 4.2 | Section Serveur | L'URL du serveur s'affiche |
| 4.3 | Section Compte | L'email s'affiche + bouton "Deconnecter" |
| 4.4 | Section Frequence de sync | 4 options radio (15min, 30min, 1h, 2h), 15min selectionne par defaut |
| 4.5 | Changer la frequence a 30 min | Le radio button change, la preference est sauvegardee |
| 4.6 | Section Sync initial : cliquer "LANCER IMPORT INITIAL" | Barre de progression, import des 30 derniers jours |
| 4.7 | Attendre la fin de l'import | Message de succes, verifier les donnees cote Momentum |
| 4.8 | Cliquer "VOIR LES LOGS" | Navigation vers l'ecran des logs avec l'historique des syncs |
| 4.9 | Cliquer "Deconnecter" | Retour a l'ecran Setup, preferences effacees |

### Test 5 : Sync en arriere-plan (Story 1.3)

**Objectif** : Verifier que la sync periodique fonctionne

| # | Action | Resultat attendu |
|---|--------|-----------------|
| 5.1 | Configurer l'app et accorder les permissions | App fonctionnelle |
| 5.2 | Fermer l'app (ne pas la killer) | L'app passe en arriere-plan |
| 5.3 | Attendre 15+ minutes | WorkManager lance la sync periodique |
| 5.4 | Verifier cote Momentum | De nouvelles donnees apparaissent |
| 5.5 | Verifier les logs de l'app | Un log "PERIODIC_SYNC / SUCCESS" apparait |
| 5.6 | Couper le reseau puis le retablir | WorkManager attend le reseau puis relance la sync |

---

## 4. Cas limites a tester

| Scenario | Comportement attendu |
|----------|---------------------|
| Serveur Momentum eteint | Erreur de connexion, pas de crash |
| Token JWT expire | Re-login automatique dans SyncWorker |
| Health Connect desinstalle | Message d'erreur grace, app ne crash pas |
| Pas de donnees sante | Metriques a 0, pas de crash |
| Double sync rapide | La deuxieme est ignoree (WorkManager unique work) |
| Changement de frequence | WorkManager met a jour la periodicite |
| Import initial annule (quitter l'ecran) | Le ViewModel est detruit, pas de fuite memoire |

---

## 5. Verification cote Momentum (API)

Pour verifier que les donnees arrivent bien, on peut utiliser curl :

```bash
# Login
curl -k -X POST https://localhost:3001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"xxx"}'

# Verifier le status de sync
curl -k -X GET https://localhost:3001/health-sync/status \
  -H "Authorization: Bearer <token>"

# Verifier les activites synchro
curl -k -X GET "https://localhost:3001/health-sync/activities?from=2026-02-01&to=2026-02-06" \
  -H "Authorization: Bearer <token>"
```

---

## 6. Problemes connus

| Probleme | Cause | Workaround |
|----------|-------|------------|
| "Health Connect non disponible" | Emulateur sans HC ou Android < 14 | Utiliser un appareil physique Android 14+ |
| Changement d'URL serveur ne prend pas effet | Le client Retrofit est singleton | Forcer l'arret de l'app et la relancer |
| Background sync ne se lance pas | Permissions HC `READ_HEALTH_DATA_IN_BACKGROUND` requiert approbation Google | En dev, la sync manuelle fonctionne comme alternative |
| SSL handshake failure | Certificat self-signed sans la checkbox cochee | Cocher "Accepter les certificats self-signed" dans Setup |

---

## 7. Structure des commits

```
bb7b456 fix: align LoginResponse with Momentum API contract
8f69d9c fix: resolve compilation and runtime issues from audit
ca0773c docs: update story statuses to Implemented and add progress tracker
58751e0 test: add unit tests and README
79f871a feat: implement full Android companion app (Stories 1.1-1.5)
803004a feat: Add Dashboard and Manual Sync functionality
70f7a01 docs: add BMAD framework, PRD, architecture, and stories
```
