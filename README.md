Trapper Keeper
=============

What?
-----
Trapper Keeper is an image server written in Clojure and supplies an API with the following abilities:
- Public file upload
- Image information
- Image display
- Image deletion
- Dynamic image "filers", which will allow you modify the image on the fly without having to upload multiple files for things like resizing or thumbnails. 

Why?
----
Imgur and the like are great, but someimes you want to hold the images yourself. This allows for a feature-parity version of the popular image upload services, with a little extra and the security of hosting it yourself.

How?
----
1. You'll need to have the standard Clojure web stack.
2. There is some configuration at the top of the core.clj file for things like where you want to store your image and cache, or what the list of image types you wish to allow for upload.

To Do
-----
1. Refactor filters (multimethod? Something else?).
2. Move configuration options to a config file outside of the code.
3. Add real logging (instead of using std out)
4. Upload war file.
5. Add option to have custom filename
6. Add option to have custom sub-bucket path.
7. BUG: Allow uppercase extensions!