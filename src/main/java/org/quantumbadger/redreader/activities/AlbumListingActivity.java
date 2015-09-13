/*******************************************************************************
 * This file is part of RedReader.
 *
 * RedReader is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * RedReader is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with RedReader.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package org.quantumbadger.redreader.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import org.apache.http.StatusLine;
import org.quantumbadger.redreader.adapters.AlbumAdapter;
import org.quantumbadger.redreader.cache.RequestFailureType;
import org.quantumbadger.redreader.common.*;
import org.quantumbadger.redreader.image.GetAlbumInfoListener;
import org.quantumbadger.redreader.image.ImgurAPI;

import java.util.regex.Matcher;

public class AlbumListingActivity extends BaseActivity {

	private String mUrl;
	private boolean mHaveReverted = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		PrefsUtility.applyTheme(this);

		getActionBar().setHomeButtonEnabled(true);
		getActionBar().setDisplayHomeAsUpEnabled(true);

		final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		final boolean solidblack = PrefsUtility.appearance_solidblack(this, sharedPreferences)
				&& PrefsUtility.appearance_theme(this, sharedPreferences) == PrefsUtility.AppearanceTheme.NIGHT;

		if(solidblack) getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

		final Intent intent = getIntent();

		mUrl = intent.getDataString();

		if(mUrl == null) {
			finish();
			return;
		}

		final Matcher matchImgur = LinkHandler.imgurAlbumPattern.matcher(mUrl);
		final String albumId;

		if(matchImgur.find()) {
			albumId = matchImgur.group(1);
		} else {
			revertToWeb();
			return;
		}

		Log.i("AlbumListingActivity", "Loading URL " + mUrl + ", album id " + albumId);

		final ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);

		final LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.addView(progressBar);

		ImgurAPI.getAlbumInfo(this, albumId, Constants.Priority.IMAGE_VIEW, 0, new GetAlbumInfoListener() {

			@Override
			public void onFailure(final RequestFailureType type, final Throwable t, final StatusLine status, final String readableMessage) {
				revertToWeb();
			}

			@Override
			public void onSuccess(final ImgurAPI.AlbumInfo info) {
				Log.i("AlbumListingActivity", "Got album, " + info.images.size() + " image(s)");

				AndroidApi.UI_THREAD_HANDLER.post(new Runnable() {
					@Override
					public void run() {

						if(info.title != null && !info.title.trim().isEmpty()) {
							OptionsMenuUtility.fixActionBar(AlbumListingActivity.this, "Imgur album: " + info.title);
						} else {
							OptionsMenuUtility.fixActionBar(AlbumListingActivity.this, "Imgur album");
						}

						layout.removeAllViews();

						final ListView listView = new ListView(AlbumListingActivity.this);
						listView.setAdapter(new AlbumAdapter(info));
						layout.addView(listView);

						listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
							@Override
							public void onItemClick(
									final AdapterView<?> parent,
									final View view,
									final int position,
									final long id) {

								LinkHandler.onLinkClicked(
										AlbumListingActivity.this,
										info.images.get(position).urlOriginal);
							}
						});
					}
				});
			}
		});

		setContentView(layout);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch(item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onBackPressed() {
		if(General.onBackPressed()) super.onBackPressed();
	}

	private void revertToWeb() {

		final Runnable r = new Runnable() {
			public void run() {
				if(!mHaveReverted) {
					mHaveReverted = true;
					LinkHandler.onLinkClicked(AlbumListingActivity.this, mUrl, true);
					finish();
				}
			}
		};

		if(General.isThisUIThread()) {
			r.run();
		} else {
			AndroidApi.UI_THREAD_HANDLER.post(r);
		}
	}
}