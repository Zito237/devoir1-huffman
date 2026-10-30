# Explication complète du projet Huffman

Ce fichier explique ce que fait le projet, comment le lancer, comment fonctionne le code,
ce qui a été mesuré et quels défauts restent à connaître.
Le [README.md](README.md) reste la référence pour la preuve d'optimalité (section 4) et les
questions d'oral (section 10).

## 1. Le projet en bref

C'est un **compresseur de fichiers sans perte** écrit en Java pur (aucune bibliothèque externe).
Il offre trois commandes :

| Commande | Rôle |
|---|---|
| `compress` | transforme un fichier en fichier `.huf` plus petit |
| `decompress` | retrouve le fichier d'origine, à l'octet près |
| `stats` | affiche la table des codes et la compare à la théorie |

**L'idée.** Normalement, chaque caractère occupe 8 bits, qu'il soit fréquent ou rare. Huffman
donne des codes **courts aux caractères fréquents** et **longs aux caractères rares**.
Dans le fichier d'exemple, l'espace (2 640 fois) coûte 3 bits, alors que `h` (40 fois) en coûte 9.
En moyenne, on passe de 8 à environ 4,39 bits par caractère.

## 2. Installation et lancement

### Ce qu'il fallait sur cette machine

| Élément | État constaté | Action |
|---|---|---|
| JDK 17 ou plus | Le JDK 17.0.5 était déjà installé, mais `java` lançait le JDK 11 | Rien à télécharger, voir ci-dessous |
| Extension Java de VS Code | Absente | Installation de `vscjava.vscode-java-pack` |
| Bibliothèques | Aucune nécessaire | Rien |

**Pourquoi le JDK 11 ne suffit pas :** `Main.java` utilise `case "x" -> { ... }` (Java 14) et les
blocs de texte `"""` (Java 15). Avec le JDK 11, la compilation s'arrête sur
`error: : expected`.

**Le problème de `PATH` :** le JDK 11 est placé avant le 17 dans le `PATH` système, et le modifier
demande des droits administrateur. Le fichier `.vscode/settings.json` a donc reçu deux réglages :

- `java.configuration.runtimes` indique le JDK 17 à l'extension Java ;
- `terminal.integrated.env.windows` place le JDK 17 en tête dans les **nouveaux** terminaux VS Code.

### Lancer le projet

**Depuis un nouveau terminal VS Code** (PowerShell) :

```powershell
.\build.bat
java -cp bin huffman.Main compress   exemples/texte.txt     exemples/texte.txt.huf
java -cp bin huffman.Main decompress exemples/texte.txt.huf exemples/texte_restaure.txt
java -cp bin huffman.Main stats      exemples/texte.txt
```

**Depuis un terminal hors de VS Code**, lancez d'abord cette ligne (valable pour ce terminal seulement) :

```powershell
$env:Path = "C:\Program Files\Java\jdk-17.0.5\bin;$env:Path"
```

**Depuis l'interface VS Code :** ouvrez le dossier `huffman`, puis Ctrl+Shift+D et choisissez une
configuration (*Tests Huffman*, *Compresser…*, *Décompresser…*, *Statistiques…*).
Cette méthode n'a pas été essayée : tous les résultats ci-dessous viennent de la ligne de commande.

### Si les accents s'affichent mal (`r�ussi`)

C'est un problème d'affichage uniquement : les données ne sont pas touchées.
L'option `-Dstdout.encoding=UTF-8` présente dans `build.bat` n'a d'effet qu'à partir du JDK 19.
Sous JDK 17, utilisez ceci :

```powershell
[Console]::OutputEncoding = [Text.Encoding]::UTF8
java -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp bin huffman.Main stats exemples/texte.txt
```

## 3. Résultats de l'exécution

Exécution du 30/10/2026 avec le JDK 17.0.5.

**Tests :** 14 réussis, 0 échoué.

**Fichier d'exemple `exemples/texte.txt` :**

| Mesure | Valeur |
|---|---|
| Taille originale | 15 760 octets |
| Taille compressée | 8 913 octets, dont 268 d'en-tête |
| Ratio | 56,55 % (fichier réduit de 43 %) |
| Entropie H (limite théorique) | 4,3452 bits par symbole |
| Longueur moyenne L obtenue | 4,3883 bits par symbole |
| Vérification de `H <= L < H+1` | OK |
| Aller-retour | SHA-256 identiques entre l'original et le fichier restauré |

**Autres essais :**

| Entrée | Résultat | À retenir |
|---|---|---|
| `ABRACADABRA` (11 octets) | 271 octets, soit 2463 % | L'en-tête de 268 octets rend les petits fichiers plus gros |
| Fichier vide | 268 octets | En-tête seul |
| `aaaa` | Code de 1 bit, `stats` affiche `NON (cas 1 symbole…)` | Cas attendu : L=1 et H=0, donc L < H+1 est faux |
| 1 Mo aléatoire | 1 048 844 octets, soit 100,03 % | Aucun gain sur des données aléatoires |
| Fichier inexistant | `Erreur : nope.txt`, code de sortie 2 | Message peu parlant |
| Texte passé à `decompress` | `Ce fichier n'est pas un fichier compressé HUF1` | Bien rejeté, voir le défaut 2 |

## 4. Comment fonctionne l'algorithme

Exemple complet avec `ABRACADABRA`.

**Étape 1 : compter.** A:5, B:2, R:2, C:1, D:1.

**Étape 2 : construire l'arbre.** On fusionne les deux éléments les plus légers, jusqu'à n'en avoir
plus qu'un :

```
C(1) + D(1)    -> CD(2)
B(2) + CD(2)   -> BCD(4)
R(2) + BCD(4)  -> RBCD(6)
A(5) + RBCD(6) -> racine(11)

        racine(11)
        /       \
      A(5)     RBCD(6)
               /     \
             R(2)   BCD(4)
                    /    \
                  B(2)   CD(2)
                         /   \
                       C(1)  D(1)
```

**Étape 3 : relever les longueurs.** La longueur d'un code est la profondeur de la feuille :
A=1, R=2, B=3, C=4, D=4.

**Étape 4 : attribuer les codes canoniques.** On trie par longueur, puis par symbole, et on numérote :

| Symbole | Longueur | Code |
|---|---|---|
| A | 1 | `0` |
| R | 2 | `10` |
| B | 3 | `110` |
| C | 4 | `1110` |
| D | 4 | `1111` |

**Étape 5 : écrire les bits.**

```
A  B    R   A  C     A  D     A  B    R   A
0  110  10  0  1110  0  1111  0  110  10  0     = 23 bits

01101001 11001111 01101000     (le dernier octet est complété par 1 zéro)
   69       CF       68
```

Ces trois octets sont bien les derniers du fichier compressé réellement produit : 23 bits au lieu de 88.

**Étape 6 : décoder.** Aucun code n'est le début d'un autre, donc on lit les bits jusqu'à reconnaître
un code. La taille stockée dans l'en-tête indique où s'arrêter, ce qui évite de décoder le zéro
de complément comme un `A` supplémentaire.

**Pourquoi des codes canoniques ?** Le décompresseur doit retrouver les codes. Comme l'étape 4 est
déterministe, il suffit de lui transmettre les 256 longueurs, sans l'arbre.

## 5. Format d'un fichier `.huf`

```
"HUF1"   4 octets     signature (48 55 46 31)
taille   8 octets     taille d'origine (00 00 00 00 00 00 3D 90 = 15 760 dans l'exemple)
L[0..255] 256 octets  longueur du code de chaque octet, 0 si absent
données  le reste     codes à la suite, dernier octet complété par des 0
```

L'en-tête fait toujours **268 octets**.

## 6. Le code, fichier par fichier

Le trajet des données à la compression :

```
Main -> HuffmanCompressor.compressFile
          |-- countFrequencies        passe 1 : compte les octets
          `-- encode
                |-- HuffmanCode.fromFrequencies  MinHeap + Node -> longueurs -> codes
                |-- écriture de l'en-tête
                `-- BitOutputStream              passe 2 : écrit les codes
```

| Fichier | Ce qu'il fait | Points à bien comprendre |
|---|---|---|
| [Main.java](src/huffman/Main.java) | Lit la commande et affiche les résultats | `printStats` calcule H et L |
| [HuffmanCompressor.java](src/huffman/HuffmanCompressor.java) | Format `.huf`, enchaînement des étapes | `b & 0xFF` convertit un `byte` signé en indice 0..255 |
| [HuffmanCode.java](src/huffman/HuffmanCode.java) | Cœur de l'algorithme | `fromFrequencies`, `buildCanonicalCodes`, `decodeSymbol` |
| [MinHeap.java](src/huffman/MinHeap.java) | Tas minimum écrit à la main | `siftUp` et `siftDown` sont en O(log n) |
| [Node.java](src/huffman/Node.java) | Nœud de l'arbre | `compareTo` départage les égalités, donc le résultat est reproductible |
| [BitOutputStream.java](src/huffman/BitOutputStream.java) | Écrit bit par bit | Accumule 8 bits puis écrit un octet |
| [BitInputStream.java](src/huffman/BitInputStream.java) | Lit bit par bit | Symétrique, bit de poids fort d'abord |
| [HuffmanTests.java](test/huffman/HuffmanTests.java) | 14 tests sans JUnit | Aller-retours et propriétés théoriques |

**Les trois morceaux à savoir expliquer :**

- **`fromFrequencies`** ([HuffmanCode.java:51](src/huffman/HuffmanCode.java#L51)) : la boucle
  `while (heap.size() > 1)` est l'algorithme de Huffman. Le cas d'un seul symbole est traité à part,
  car sa feuille aurait une profondeur 0, donc un code vide.
- **`buildCanonicalCodes`** ([HuffmanCode.java:129](src/huffman/HuffmanCode.java#L129)) : vérifie
  l'inégalité de Kraft pour rejeter un en-tête corrompu, puis numérote les codes. `code <<= 1`
  ajoute un bit quand on passe à la longueur suivante.
- **`decodeSymbol`** ([HuffmanCode.java:224](src/huffman/HuffmanCode.java#L224)) : les codes d'une même
  longueur sont consécutifs. Après L bits de valeur v, on teste si `v - firstCode[L]` tombe dans la
  plage de cette longueur. Si oui, le symbole est trouvé, sans aucun arbre en mémoire.

## 7. Les tests

| Groupe | Tests |
|---|---|
| Cas limites | fichier vide, un octet, un symbole répété, deux symboles |
| Données réelles | texte français UTF-8, les 256 octets, 1 Mo aléatoire |
| Arbre extrême | fréquences de Fibonacci (profondeur 24) |
| Théorie | propriété du préfixe, égalité de Kraft, borne de Shannon, 23 bits d'ABRACADABRA |
| Robustesse | rejet d'un fichier non compressé, rejet d'un fichier tronqué |

Ces tests appellent les fonctions en mémoire : ils ne passent ni par `compressFile`, ni par
`decompressFile`, ni par `Main`. Les défauts 1 et 2 ci-dessous leur échappent donc.

## 8. Défauts constatés

Rien n'a été corrigé dans le code Java. Ces défauts sont utiles à connaître avant l'oral.

1. **Perte de données si l'entrée et la sortie sont le même fichier (grave).**
   `compress a.txt a.txt` a réduit un fichier de 15 760 octets à 268 octets, sans erreur et avec un
   message de réussite. La sortie vide le fichier avant sa relecture, à la seconde passe
   ([HuffmanCompressor.java:45-46](src/huffman/HuffmanCompressor.java#L45-L46)). Le contenu est perdu.
2. **Sortie écrasée même en cas d'échec.** `decompress` sur un fichier invalide crée un fichier de
   sortie vide. Si ce fichier existait déjà, son contenu est perdu, car la sortie est ouverte avant
   la lecture de l'en-tête ([HuffmanCompressor.java:52-53](src/huffman/HuffmanCompressor.java#L52-L53)).
3. **Estimation de `stats` parfois fausse d'un octet.** Elle annonce 8 646 octets de données pour
   8 645 réels (69 160 bits exactement). C'est un arrondi : le calcul passe par des `double`
   ([Main.java:116](src/huffman/Main.java#L116)).
4. **Option d'encodage sans effet sous JDK 17** dans `build.bat`, `build.sh` et `launch.json`
   (voir la section 2).
5. **Message peu clair pour un fichier introuvable** : `Erreur : nope.txt`.

## 9. Limites de la méthode

- L'en-tête fixe de 268 octets pénalise les petits fichiers (voir ABRACADABRA).
- Le fichier est lu deux fois, ce qui est impossible sur un flux qu'on ne peut pas relire.
- Il faut au moins 1 bit par symbole.
- Huffman compte les octets mais ne voit pas les répétitions. Le fichier d'exemple répète 40 fois
  les mêmes 3 lignes, et il ne descend pourtant qu'à 56,55 %. C'est pourquoi ZIP et gzip combinent
  LZ77 (répétitions) et Huffman.
